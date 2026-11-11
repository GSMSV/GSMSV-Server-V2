package com.gsmsv.api.database.service

import com.gsmsv.api.database.backup.BackupStorage
import com.gsmsv.api.database.backup.DatabaseDumper
import com.gsmsv.api.database.config.DatabaseBackupProperties
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseBackup
import com.gsmsv.api.database.domain.DatabaseBackupKind
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.repository.DatabaseBackupRepository
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.global.authorization.Owner
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseBackupServiceTest {
    private val now = Instant.parse("2026-11-11T10:00:00Z")
    private val databaseRepository = mockk<DatabaseRepository>()
    private val backupRepository = mockk<DatabaseBackupRepository>()
    private val storage = MemoryStorage()
    private val dumper = TextDumper()
    private val service =
        DatabaseBackupService(
            databaseRepository,
            backupRepository,
            storage,
            dumper,
            DatabaseBackupProperties(directory = "x", retentionDays = 7),
            Clock.fixed(now, ZoneOffset.UTC),
        )

    @Test
    fun `오늘 백업이 없는 활성 DB마다 일일 백업을 만든다`() {
        val database = activeDatabase("db-aaaaaaaaaaaa")
        every { databaseRepository.findAllByStatusIn(any()) } returns listOf(database)
        every {
            backupRepository.existsByDatabaseIdAndKindAndCreatedAtGreaterThanEqual(
                database.id,
                DatabaseBackupKind.DAILY,
                Instant.parse("2026-11-11T00:00:00Z"),
            )
        } returns false
        val saved = slot<DatabaseBackup>()
        every { backupRepository.saveAndFlush(capture(saved)) } answers { saved.captured }

        assertEquals(1, service.backupAll())

        assertEquals(database.id, saved.captured.databaseId)
        assertEquals(DatabaseBackupKind.DAILY, saved.captured.kind)
        assertEquals(now.plusSeconds(7 * 24 * 3600L), saved.captured.expiresAt)
        // gzip으로 묶은 덤프가 저장소에 들어 있다
        val stored = storage.files.getValue(saved.captured.storageKey)
        assertEquals("dump:${database.id}", GZIPInputStream(stored.inputStream()).use { String(it.readBytes()) })
        assertEquals(stored.size.toLong(), saved.captured.sizeBytes)
    }

    @Test
    fun `오늘 이미 백업한 DB는 건너뛴다`() {
        every { databaseRepository.findAllByStatusIn(any()) } returns listOf(activeDatabase("db-aaaaaaaaaaaa"))
        every { backupRepository.existsByDatabaseIdAndKindAndCreatedAtGreaterThanEqual(any(), any(), any()) } returns true

        assertEquals(0, service.backupAll())

        assertTrue(storage.files.isEmpty())
    }

    @Test
    fun `한 DB의 덤프가 실패해도 다음 DB를 계속하고 반쯤 쓴 파일은 남지 않는다`() {
        val broken = activeDatabase("db-aaaaaaaaaaaa")
        val healthy = activeDatabase("db-bbbbbbbbbbbb")
        every { databaseRepository.findAllByStatusIn(any()) } returns listOf(broken, healthy)
        every { backupRepository.existsByDatabaseIdAndKindAndCreatedAtGreaterThanEqual(any(), any(), any()) } returns false
        every { backupRepository.saveAndFlush(any<DatabaseBackup>()) } answers { firstArg() }
        dumper.failFor = broken.id

        assertEquals(1, service.backupAll())

        assertEquals(1, storage.files.size)
    }

    @Test
    fun `기록 저장이 실패하면 쓴 파일을 지운다`() {
        val database = activeDatabase("db-aaaaaaaaaaaa")
        every { backupRepository.saveAndFlush(any<DatabaseBackup>()) } throws IllegalStateException("저장 실패")

        runCatching { service.createPreRestoreBackup(database) }

        assertTrue(storage.files.isEmpty())
    }

    @Test
    fun `만료된 백업은 파일과 기록을 함께 지운다`() {
        val expired =
            DatabaseBackup("backup-aaaaaaaa", "db-aaaaaaaaaaaa", DatabaseBackupKind.DAILY, 1, "k.sql.gz", now, now)
        storage.files["k.sql.gz"] = byteArrayOf(1)
        every { backupRepository.findExpired(now, now.minusSeconds(7 * 24 * 3600L)) } returns listOf(expired)
        every { backupRepository.delete(expired) } returns Unit

        assertEquals(1, service.deleteExpired())

        assertTrue(storage.files.isEmpty())
        verify { backupRepository.delete(expired) }
    }

    private fun activeDatabase(id: String): Database =
        Database
            .create(id, "이름", Owner.User("user-aaaaaaaaaaaa"), DatabaseEngine.MYSQL, "user-aaaaaaaaaaaa", now.plusSeconds(1000))
            .also { it.markActive() }

    private class MemoryStorage : BackupStorage {
        val files = mutableMapOf<String, ByteArray>()

        override fun write(
            key: String,
            writer: (OutputStream) -> Unit,
        ): Long {
            val buffer = ByteArrayOutputStream()
            writer(buffer)
            files[key] = buffer.toByteArray()
            return buffer.size().toLong()
        }

        override fun read(key: String): InputStream = files.getValue(key).inputStream()

        override fun delete(key: String) {
            files.remove(key)
        }
    }

    private class TextDumper : DatabaseDumper {
        var failFor: String? = null

        override fun dump(
            databaseId: String,
            output: OutputStream,
        ) {
            check(databaseId != failFor) { "덤프 실패" }
            output.write("dump:$databaseId".toByteArray())
        }

        override fun restore(
            databaseId: String,
            input: InputStream,
        ) = Unit
    }
}
