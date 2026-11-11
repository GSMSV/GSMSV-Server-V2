package com.gsmsv.api.database.service

import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseBackup
import com.gsmsv.api.database.domain.DatabaseBackupKind
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.repository.DatabaseBackupRepository
import com.gsmsv.api.global.authorization.Owner
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class DatabaseBackupApiServiceTest {
    private val now = Instant.parse("2026-11-11T10:00:00Z")
    private val backupRepository = mockk<DatabaseBackupRepository>()
    private val service = DatabaseBackupApiService(backupRepository, Clock.fixed(now, ZoneOffset.UTC))
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

    private fun backup(
        id: String,
        kind: DatabaseBackupKind,
        size: Long,
    ) = DatabaseBackup(id, "db-aaaaaaaaaaaa", kind, size, "$id.sql.gz", now.minusSeconds(60), now.plusSeconds(60))
}
