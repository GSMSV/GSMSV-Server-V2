package com.gsmsv.api.database.service

import com.gsmsv.api.audit.domain.AuditEventType
import com.gsmsv.api.audit.service.AuditRecorder
import com.gsmsv.api.database.backup.BackupStorage
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseBackup
import com.gsmsv.api.database.domain.DatabaseBackupKind
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.repository.DatabaseBackupRepository
import com.gsmsv.api.global.authorization.LoadedSubResource
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.ObjectProvider
import java.io.ByteArrayInputStream
import java.nio.file.NoSuchFileException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DatabaseBackupApiServiceTest {
    private val now = Instant.parse("2026-11-11T10:00:00Z")
    private val backupRepository = mockk<DatabaseBackupRepository>()
    private val auditRecorder = mockk<AuditRecorder>()
    private val storage = mockk<BackupStorage>()
    private val storageProvider = mockk<ObjectProvider<BackupStorage>> { every { getIfAvailable() } returns storage }
    private val service =
        DatabaseBackupApiService(backupRepository, auditRecorder, storageProvider, Clock.fixed(now, ZoneOffset.UTC))
    private val actor = UserActor("user-aaaaaaaaaaaa")
    private val database =
        Database.create(
            "db-aaaaaaaaaaaa",
            "이름",
            Owner.User("user-aaaaaaaaaaaa"),
            DatabaseEngine.MYSQL,
            "user-aaaaaaaaaaaa",
            now.plusSeconds(1000),
        )

    @Test
    fun `로더가 돌려준 DB의 id와 지금 시각으로만 조회한다`() {
        every {
            backupRepository.findAllByDatabaseIdAndExpiresAtAfterOrderByCreatedAtDescIdDesc("db-aaaaaaaaaaaa", now)
        } returns
            listOf(
                backup("backup-aaaaaaaa", DatabaseBackupKind.PRE_RESTORE, 13_002_342),
                backup("backup-bbbbbbbb", DatabaseBackupKind.DAILY, 100),
            )

        val items = service.list(database).items

        assertEquals(listOf("backup-aaaaaaaa", "backup-bbbbbbbb"), items.map { it.id })
        assertEquals(listOf("pre_restore", "daily"), items.map { it.kind })
        assertEquals(12.4, items[0].sizeMb)
        // 작은 백업도 0으로 보이지 않는다
        assertEquals(0.0, items[1].sizeMb)
    }

    @Test
    fun `백업이 없으면 빈 목록이다`() {
        every { backupRepository.findAllByDatabaseIdAndExpiresAtAfterOrderByCreatedAtDescIdDesc(any(), any()) } returns emptyList()

        assertEquals(emptyList(), service.list(database).items)
    }

    @Test
    fun `내려받을 때마다 하위 자원 표기로 감사 로그를 남기고 저장소 파일을 연다`() {
        val backup = backup("backup-aaaaaaaa", DatabaseBackupKind.DAILY, 3)
        val content = ByteArrayInputStream(byteArrayOf(1, 2, 3))
        every { auditRecorder.record(any(), any(), any(), any()) } just runs
        every { storage.read("backup-aaaaaaaa.sql.gz") } returns content

        val file = service.download(actor, LoadedSubResource(database, backup))
        service.download(actor, LoadedSubResource(database, backup))

        assertEquals("backup-aaaaaaaa.sql.gz", file.fileName)
        assertEquals(3, file.sizeBytes)
        assertSame(content, file.content)
        verify(exactly = 2) {
            auditRecorder.record(
                actor,
                AuditEventType.DATABASE_BACKUP_DOWNLOAD,
                "grn:user-aaaaaaaaaaaa:db-aaaaaaaaaaaa/backup-aaaaaaaa",
                emptyMap(),
            )
        }
    }

    @Test
    fun `보관 기간이 지난 백업은 404이고 감사 로그를 남기지 않는다`() {
        val expired = backup("backup-aaaaaaaa", DatabaseBackupKind.DAILY, 3, expiresAt = now)

        val error = assertThrows<BusinessException> { service.download(actor, LoadedSubResource(database, expired)) }

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.errorCode)
        verify(exactly = 0) { auditRecorder.record(any(), any(), any(), any()) }
        verify(exactly = 0) { storage.read(any()) }
    }

    @Test
    fun `파일을 열지 못하면 예외를 던져 감사 로그 트랜잭션도 함께 실패하게 한다`() {
        val backup = backup("backup-aaaaaaaa", DatabaseBackupKind.DAILY, 3)
        every { auditRecorder.record(any(), any(), any(), any()) } just runs
        every { storage.read(any()) } throws NoSuchFileException("backup-aaaaaaaa.sql.gz")

        assertThrows<NoSuchFileException> { service.download(actor, LoadedSubResource(database, backup)) }
    }

    private fun backup(
        id: String,
        kind: DatabaseBackupKind,
        size: Long,
        expiresAt: Instant = now.plusSeconds(60),
    ) = DatabaseBackup(id, "db-aaaaaaaaaaaa", kind, size, "$id.sql.gz", now.minusSeconds(60), expiresAt)
}
