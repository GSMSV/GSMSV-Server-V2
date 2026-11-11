package com.gsmsv.api.database.service

import com.gsmsv.api.database.backup.BackupStorage
import com.gsmsv.api.database.backup.DatabaseDumper
import com.gsmsv.api.database.config.DatabaseBackupProperties
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseBackup
import com.gsmsv.api.database.domain.DatabaseBackupKind
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.domain.DatabaseIsolation
import com.gsmsv.api.database.domain.DatabaseStatus
import com.gsmsv.api.database.repository.DatabaseBackupRepository
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.SubResourceType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.GZIPOutputStream

private val log = KotlinLogging.logger {}

/**
 * DB 백업을 만들고 정리한다 (24번 DBaaS 3.7). 사용자가 직접 만드는 백업은 없다.
 *
 * 덤프는 `application/gzip` SQL 덤프다. 파일을 먼저 쓰고 기록을 저장하며, 기록 저장이 실패하면 파일을 지운다.
 */
@Transactional
class DatabaseBackupService(
    private val databaseRepository: DatabaseRepository,
    private val backupRepository: DatabaseBackupRepository,
    private val storage: BackupStorage,
    private val dumper: DatabaseDumper,
    private val properties: DatabaseBackupProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * 오늘(UTC) 백업이 없는 활성·정지 DB마다 일일 백업을 만든다. 한 DB가 실패해도 다음 DB를 계속한다.
     * 만든 백업 수를 돌려준다.
     */
    fun backupAll(): Int {
        val startOfToday =
            clock
                .instant()
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
        return databaseRepository
            .findAllByStatusIn(BACKUP_TARGET_STATUSES)
            .filter { it.isBackupTarget() }
            .count { database ->
                try {
                    val done =
                        backupRepository.existsByDatabaseIdAndKindAndCreatedAtGreaterThanEqual(
                            database.id,
                            DatabaseBackupKind.DAILY,
                            startOfToday,
                        )
                    if (!done) createBackup(database.id, DatabaseBackupKind.DAILY)
                    !done
                } catch (e: Exception) {
                    // 비밀번호·주소가 들어갈 수 있는 예외 메시지는 남기지 않는다
                    log.error { "DB 백업에 실패했습니다. id=${database.id} 원인=${e::class.simpleName}" }
                    false
                }
            }
    }

    /** 복원하기 직전 상태를 남기는 백업 (7.12) */
    fun createPreRestoreBackup(database: Database): DatabaseBackup = createBackup(database.id, DatabaseBackupKind.PRE_RESTORE)

    /** 보관 기간이 지난 백업의 파일과 기록을 지운다. 삭제된 DB의 백업은 삭제 뒤 보관 기간까지 남긴다. 지운 수를 돌려준다 */
    fun deleteExpired(): Int {
        val now = clock.instant()
        val expired = backupRepository.findExpired(now, now.minus(retention()))
        expired.forEach { backup ->
            try {
                storage.delete(backup.storageKey)
                backupRepository.delete(backup)
            } catch (e: Exception) {
                log.error { "만료된 백업을 지우지 못했습니다. id=${backup.id} 원인=${e::class.simpleName}" }
            }
        }
        return expired.size
    }

    private fun createBackup(
        databaseId: String,
        kind: DatabaseBackupKind,
    ): DatabaseBackup {
        val id = IdGenerator.subResourceId(SubResourceType.BACKUP)
        val key = "$id.sql.gz"
        val size =
            storage.write(key) { out ->
                GZIPOutputStream(out).use { dumper.dump(databaseId, it) }
            }
        val now = clock.instant()
        try {
            return backupRepository.saveAndFlush(
                DatabaseBackup(id, databaseId, kind, size, key, now, now.plus(retention())),
            )
        } catch (e: RuntimeException) {
            storage.delete(key)
            throw e
        }
    }

    private fun Database.isBackupTarget() = isolation == DatabaseIsolation.SHARED && engine == DatabaseEngine.MYSQL

    private fun retention(): Duration = Duration.ofDays(properties.retentionDays.toLong())

    private companion object {
        val BACKUP_TARGET_STATUSES = setOf(DatabaseStatus.ACTIVE, DatabaseStatus.SUSPENDED)
    }
}
