package com.gsmsv.api.network.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementCallback
import org.springframework.stereotype.Repository

@Repository
class HttpsRouteLock(
    private val jdbcTemplate: JdbcTemplate,
) {
    /**
     * 라우트 목록을 바꾸는 트랜잭션을 하나씩 차례로 돌린다. 트랜잭션이 끝나면 풀린다.
     *
     * 라우트는 전체를 통째로 교체하므로, 두 요청이 서로의 새 행을 못 본 채 목록을 보내면 나중 요청이 앞 요청의 라우트를 지운다.
     * VM당 개수 검사와 삽입도 이 잠금 안에서 함께 직렬화된다.
     */
    fun lock() {
        jdbcTemplate.execute(
            "select pg_advisory_xact_lock(hashtext(?))",
            PreparedStatementCallback { statement ->
                statement.setString(1, LOCK_KEY)
                statement.execute()
            },
        )
    }

    private companion object {
        const val LOCK_KEY = "network:https-routes"
    }
}
