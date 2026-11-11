package com.gsmsv.api.database.service

import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseBackup
import com.gsmsv.api.database.dto.DatabaseBackupListResponse
import com.gsmsv.api.database.dto.DatabaseBackupResponse
import com.gsmsv.api.database.repository.DatabaseBackupRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import kotlin.math.round

/**
 * DB 백업 목록 (7.10). 로더가 돌려준 [Database]를 받으므로 권한 판단은 끝난 상태다.
 * 다른 DB의 백업이 섞이지 않도록 그 DB의 id로만 조회한다.
 */
@Service
class DatabaseBackupApiService(
    private val backupRepository: DatabaseBackupRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional(readOnly = true)
    fun list(database: Database): DatabaseBackupListResponse =
        DatabaseBackupListResponse(
            backupRepository
                .findAllByDatabaseIdAndExpiresAtAfterOrderByCreatedAtDescIdDesc(database.id, clock.instant())
                .map(::toResponse),
        )

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
