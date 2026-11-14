package com.gsmsv.api.database.repository

import com.gsmsv.api.database.domain.Database
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementCallback
import org.springframework.stereotype.Repository

/** 같은 소유자의 DB 발급이 겹쳐도 개수 검사와 저장이 한 번에 하나씩 일어나게 한다 */
@Repository
class DatabaseQuotaLock(
    private val jdbcTemplate: JdbcTemplate,
) {
    /** 트랜잭션이 끝날 때까지 잡는다. DB가 아직 없는 소유자도 막을 수 있게 행이 아니라 소유자 값으로 잠근다 */
    fun lock(
        ownerType: Database.OwnerType,
        ownerId: String,
    ) {
        jdbcTemplate.execute(
            "select pg_advisory_xact_lock(hashtext(?))",
            PreparedStatementCallback { statement ->
                statement.setString(1, "DATABASE:${ownerType.name}:$ownerId")
                statement.execute()
            },
        )
    }
}
