package com.gsmsv.api.network.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementCallback
import org.springframework.stereotype.Repository

@Repository
class PortForwardLock(
    private val jdbcTemplate: JdbcTemplate,
) {
    /**
     * 같은 VM의 포트포워딩 변경을 트랜잭션이 끝날 때까지 하나씩 처리한다.
     * 커스텀 개수 검사와 삽입이 겹치지 않게 하고, Go에 보내는 포트 집합이 늦게 도착한 옛 집합으로 덮이지 않게 한다.
     */
    fun lock(vmId: String) {
        jdbcTemplate.execute(
            "select pg_advisory_xact_lock(hashtext(?))",
            PreparedStatementCallback { statement ->
                statement.setString(1, "port-forward:$vmId")
                statement.execute()
            },
        )
    }
}
