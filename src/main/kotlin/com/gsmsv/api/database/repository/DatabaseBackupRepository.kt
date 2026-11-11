package com.gsmsv.api.database.repository

import com.gsmsv.api.database.domain.DatabaseBackup
import com.gsmsv.api.database.domain.DatabaseBackupKind
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/** id 조회는 [com.gsmsv.api.database.service.DatabaseBackupResourceReader] 밖에서 부르지 않는다 (22번 규칙). */
interface DatabaseBackupRepository : JpaRepository<DatabaseBackup, String> {
    fun existsByDatabaseIdAndKindAndCreatedAtGreaterThanEqual(
        databaseId: String,
        kind: DatabaseBackupKind,
        createdAt: Instant,
    ): Boolean

    /**
     * 정리할 백업. 기간이 지났어도 삭제된 DB의 백업은 삭제 뒤 [deletedCutoff]까지는 남겨 관리자가 되살릴 수 있게 한다.
     * [deletedCutoff]는 지금에서 보관 기간을 뺀 시각이다.
     */
    @Query(
        """
        select b from DatabaseBackup b
        where b.expiresAt < :now
          and not exists (
            select 1 from Database d where d.id = b.databaseId and d.deletedAt > :deletedCutoff
          )
        """,
    )
    fun findExpired(
        @Param("now") now: Instant,
        @Param("deletedCutoff") deletedCutoff: Instant,
    ): List<DatabaseBackup>
}
