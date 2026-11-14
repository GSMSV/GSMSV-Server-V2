package com.gsmsv.api.network.repository

import org.springframework.stereotype.Repository
import javax.sql.DataSource

/**
 * VM 하나의 방화벽 맞추기 요청을 한 번에 하나씩 보내게 하는 차례 잠금 (내부 API 공통 규약 4.1).
 *
 * 두 요청이 거꾸로 도착하면 옛 상태가 마지막이 되므로, 서버가 여러 대여도 지켜지게 DB 세션 잠금으로 차례를 잡는다.
 * Go 응답을 기다리는 동안 트랜잭션을 열어 두지 않도록(7장) 트랜잭션과 따로 연결 하나를 잡아 잠금만 건다.
 * 개수 검사와 추가도 이 차례 안에서 하므로 한도를 넘겨 저장되지 않는다.
 */
@Repository
class FirewallSyncLock(
    private val dataSource: DataSource,
) {
    fun <T> withLock(
        vmId: String,
        block: () -> T,
    ): T =
        dataSource.connection.use { connection ->
            execute(connection, "select pg_advisory_lock(hashtext(?))", vmId)
            try {
                block()
            } finally {
                execute(connection, "select pg_advisory_unlock(hashtext(?))", vmId)
            }
        }

    private fun execute(
        connection: java.sql.Connection,
        sql: String,
        vmId: String,
    ) {
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, "$KEY_PREFIX$vmId")
            statement.execute()
        }
    }

    private companion object {
        const val KEY_PREFIX = "firewall:"
    }
}
