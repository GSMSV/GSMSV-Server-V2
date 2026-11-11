package com.gsmsv.api.database.provisioner

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.Closeable
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

private val log = KotlinLogging.logger {}

/**
 * 공유 MySQL 서버에 JDBC로 직접 database와 계정을 만든다.
 *
 * [dataSource]는 앱 데이터소스와 분리된 관리 계정 연결이다. 식별자와 비밀번호는 [DatabaseIdentifier]로
 * 형식을 검사한 값만 SQL에 넣는다. 로그에는 DB id만 남기고 비밀번호·주소는 남기지 않는다.
 */
class JdbcMysqlProvisioner(
    private val dataSource: DataSource,
) : SharedDatabaseProvisioner,
    Closeable {
    override fun create(
        databaseId: String,
        password: String,
        maxConnections: Int,
    ) {
        DatabaseIdentifier.require(databaseId)
        DatabaseIdentifier.requirePassword(password)
        require(maxConnections > 0) { "동시 연결 수는 1 이상이어야 합니다." }

        var databaseCreated = false
        var accountCreated = false
        try {
            execute("CREATE DATABASE ${quote(databaseId)} CHARACTER SET utf8mb4")
            databaseCreated = true
            execute(
                "CREATE USER ${account(databaseId)} IDENTIFIED BY '$password' " +
                    "WITH MAX_USER_CONNECTIONS $maxConnections",
            )
            accountCreated = true
            execute("GRANT ALL PRIVILEGES ON ${quote(databaseId)}.* TO ${account(databaseId)}")
        } catch (e: SQLException) {
            // 반쯤 만든 것만 지운다. 이미 있던 database를 지우지 않도록 이번에 만든 것만 본다
            cleanup(databaseId, databaseCreated, accountCreated)
            throw DatabaseProvisioningException("공유 DB 서버에 database를 만들지 못했습니다.", e)
        }
    }

    override fun rotatePassword(
        databaseId: String,
        newPassword: String,
    ) {
        DatabaseIdentifier.require(databaseId)
        DatabaseIdentifier.requirePassword(newPassword)
        run(databaseId, "비밀번호를 바꾸지 못했습니다.") { connection ->
            connection.update("ALTER USER ${account(databaseId)} IDENTIFIED BY '$newPassword'")
            killConnections(connection, databaseId)
        }
    }

    override fun lock(databaseId: String) {
        DatabaseIdentifier.require(databaseId)
        run(databaseId, "계정을 잠그지 못했습니다.") { connection ->
            connection.update("ALTER USER ${account(databaseId)} ACCOUNT LOCK")
            killConnections(connection, databaseId)
        }
    }

    override fun unlock(databaseId: String) {
        DatabaseIdentifier.require(databaseId)
        run(databaseId, "계정 잠금을 풀지 못했습니다.") { connection ->
            connection.update("ALTER USER ${account(databaseId)} ACCOUNT UNLOCK")
        }
    }

    override fun drop(databaseId: String) {
        DatabaseIdentifier.require(databaseId)
        run(databaseId, "database를 지우지 못했습니다.") { connection ->
            killConnections(connection, databaseId)
            connection.update("DROP DATABASE IF EXISTS ${quote(databaseId)}")
            connection.update("DROP USER IF EXISTS ${account(databaseId)}")
        }
    }

    override fun close() {
        (dataSource as? AutoCloseable)?.close()
    }

    private fun cleanup(
        databaseId: String,
        databaseCreated: Boolean,
        accountCreated: Boolean,
    ) {
        try {
            if (accountCreated) execute("DROP USER IF EXISTS ${account(databaseId)}")
            if (databaseCreated) execute("DROP DATABASE IF EXISTS ${quote(databaseId)}")
        } catch (e: SQLException) {
            // 정리까지 실패하면 운영자가 찾을 수 있게 id만 남긴다
            log.error(e) { "반쯤 만든 database를 정리하지 못했습니다. id=$databaseId" }
        }
    }

    /** 이 계정의 기존 연결을 모두 끊는다. 이미 끊긴 연결은 건너뛴다 */
    private fun killConnections(
        connection: Connection,
        databaseId: String,
    ) {
        val ids = mutableListOf<Long>()
        connection.prepareStatement("SELECT id FROM information_schema.processlist WHERE user = ?").use { statement ->
            statement.setString(1, databaseId)
            statement.executeQuery().use { rows -> while (rows.next()) ids += rows.getLong(1) }
        }
        ids.forEach { id ->
            try {
                connection.update("KILL CONNECTION $id")
            } catch (_: SQLException) {
                // 조회와 KILL 사이에 이미 끝난 연결이다
            }
        }
    }

    private fun execute(sql: String) {
        dataSource.connection.use { it.update(sql) }
    }

    private fun run(
        databaseId: String,
        failure: String,
        block: (Connection) -> Unit,
    ) {
        try {
            dataSource.connection.use(block)
        } catch (e: SQLException) {
            log.warn { "공유 DB 서버 작업 실패. id=$databaseId" }
            throw DatabaseProvisioningException(failure, e)
        }
    }

    private fun Connection.update(sql: String) {
        createStatement().use { it.execute(sql) }
    }

    // 형식 검사를 통과한 id만 들어오므로 따옴표를 닫는 글자가 없다
    private fun quote(identifier: String) = "`$identifier`"

    private fun account(databaseId: String) = "'$databaseId'@'%'"
}
