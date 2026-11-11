package com.gsmsv.api.app.repository

import com.gsmsv.api.app.domain.AppOwnerType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementCallback
import org.springframework.stereotype.Repository

@Repository
class AppQuotaLock(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun lock(
        ownerType: AppOwnerType,
        ownerId: String,
    ) {
        // 앱이 아직 없는 소유자도 같은 트랜잭션에서 개수 검사와 삽입을 직렬화한다.
        jdbcTemplate.execute(
            "select pg_advisory_xact_lock(hashtext(?))",
            PreparedStatementCallback { statement ->
                statement.setString(1, "${ownerType.name}:$ownerId")
                statement.execute()
            },
        )
    }
}
