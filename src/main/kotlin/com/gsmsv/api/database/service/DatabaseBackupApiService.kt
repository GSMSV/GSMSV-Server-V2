package com.gsmsv.api.database.service

import com.gsmsv.api.audit.domain.AuditEventType
import com.gsmsv.api.audit.service.AuditRecorder
import com.gsmsv.api.database.backup.BackupStorage
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseBackup
import com.gsmsv.api.database.dto.DatabaseBackupFile
import com.gsmsv.api.database.dto.DatabaseBackupListResponse
import com.gsmsv.api.database.dto.DatabaseBackupResponse
import com.gsmsv.api.database.repository.DatabaseBackupRepository
import com.gsmsv.api.global.authorization.Actor
import com.gsmsv.api.global.authorization.LoadedSubResource
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.GrnParser
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import kotlin.math.round

/**
 * DB 백업 목록 (7.10)과 내려받기 (7.11). 로더가 돌려준 자원을 받으므로 권한 판단은 끝난 상태다.
 * 다른 DB의 백업이 섞이지 않도록 그 DB의 id로만 조회한다.
 *
 * 백업 저장소는 백업 기능을 켰을 때만 빈이 있다 (`DatabaseBackupConfig`).
 */
@Service
class DatabaseBackupApiService(
    private val backupRepository: DatabaseBackupRepository,
    private val auditRecorder: AuditRecorder,
    private val storageProvider: ObjectProvider<BackupStorage>,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional(readOnly = true)
    fun list(database: Database): DatabaseBackupListResponse =
        DatabaseBackupListResponse(
            backupRepository
                .findAllByDatabaseIdAndExpiresAtAfterOrderByCreatedAtDescIdDesc(database.id, clock.instant())
                .map(::toResponse),
        )

    /**
     * 내려받을 때마다 감사 로그를 남긴다. 기록과 파일 열기가 같은 트랜잭션이라 파일을 못 열면 기록도 남지 않는다.
     *
     * @throws BusinessException 보관 기간이 지난 백업이면 404. 목록에 없는 백업은 내려받을 수 없다
     */
    @Transactional
    fun download(
        actor: Actor,
        loaded: LoadedSubResource<Database, DatabaseBackup>,
    ): DatabaseBackupFile {
        val (database, backup) = loaded
        if (backup.isExpiredAt(clock.instant())) {
            throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }
        val storage = storageProvider.getIfAvailable() ?: error("백업 저장소가 설정되지 않았습니다.")
        auditRecorder.record(
            actor,
            AuditEventType.DATABASE_BACKUP_DOWNLOAD,
            "${GrnParser.format(database.owner.id, database.id)}/${backup.id}",
        )
        return DatabaseBackupFile(
            fileName = "${backup.id}.sql.gz",
            sizeBytes = backup.sizeBytes,
            content = storage.read(backup.storageKey),
        )
    }

    private fun toResponse(backup: DatabaseBackup) =
        DatabaseBackupResponse(
            id = backup.id,
            kind = backup.kind.name.lowercase(),
            createdAt = backup.createdAt,
            sizeMb = megabytes(backup.sizeBytes),
            expiresAt = backup.expiresAt,
        )

    // 작은 백업이 0으로 보이지 않도록 소수 둘째 자리까지 둔다
    private fun megabytes(bytes: Long): Double = round(bytes / BYTES_PER_MB * 100) / 100

    private companion object {
        const val BYTES_PER_MB = 1024.0 * 1024.0
    }
}
