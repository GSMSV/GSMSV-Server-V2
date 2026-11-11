package com.gsmsv.api.database.provisioner

import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.mysql.MySQLContainer
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcMysqlProvisionerTest {
    private val mysql = MySQLContainer("mysql:8.4")
    private lateinit var admin: HikariDataSource
    private lateinit var provisioner: JdbcMysqlProvisioner
    private val password = "Abcdefgh12345678"

    @BeforeAll
    fun start() {
        mysql.start()
        admin =
            HikariDataSource().apply {
                jdbcUrl = mysql.jdbcUrl
                username = "root"
                password = mysql.password
                maximumPoolSize = 2
            }
        provisioner = JdbcMysqlProvisioner(admin)
    }

    @AfterAll
    fun stop() {
        provisioner.close()
        mysql.stop()
    }

    @Test
    fun `만든 계정으로 접속해 자기 database만 쓸 수 있다`() {
        val id = "db-aaaaaaaaaaaa"
        provisioner.create(id, password, 10)

        connect(id, password).use { connection ->
            connection.createStatement().use { it.execute("CREATE TABLE t (id INT)") }
            // 다른 database는 볼 수 없다
            val databases =
                connection.createStatement().use { statement ->
                    statement.executeQuery("SHOW DATABASES").use { rows ->
                        generateSequence { if (rows.next()) rows.getString(1) else null }.toList()
                    }
                }
            assertTrue(id in databases)
            assertFalse("mysql" in databases)
        }
        assertEquals(10, maxUserConnections(id))
    }

    @Test
    fun `이미 있는 database를 다시 만들면 실패하고 기존 것은 그대로다`() {
        val id = "db-bbbbbbbbbbbb"
        provisioner.create(id, password, 10)

        assertFailsWith<DatabaseProvisioningException> { provisioner.create(id, password, 10) }

        connect(id, password).use { }
    }

    @Test
    fun `계정 만들기가 중간에 실패하면 만든 database를 정리한다`() {
        val id = "db-cccccccccccc"
        // 같은 이름의 계정이 먼저 있으면 database를 만든 뒤 CREATE USER에서 실패한다
        execute("CREATE USER '$id'@'%' IDENTIFIED BY '$password'")

        assertFailsWith<DatabaseProvisioningException> { provisioner.create(id, password, 10) }

        assertFalse(databaseExists(id))
        execute("DROP USER '$id'@'%'")
    }

    @Test
    fun `비밀번호를 바꾸면 옛 비밀번호로 접속할 수 없고 기존 연결이 끊긴다`() {
        val id = "db-dddddddddddd"
        provisioner.create(id, password, 10)
        val open = connect(id, password)

        provisioner.rotatePassword(id, "Zyxwvuts87654321")

        assertFailsWith<SQLException> { connect(id, password) }
        connect(id, "Zyxwvuts87654321").use { }
        assertFailsWith<SQLException> { open.createStatement().use { it.execute("SELECT 1") } }
    }

    @Test
    fun `잠그면 접속할 수 없고 풀면 다시 접속한다`() {
        val id = "db-eeeeeeeeeeee"
        provisioner.create(id, password, 10)

        provisioner.lock(id)
        assertFailsWith<SQLException> { connect(id, password) }

        provisioner.unlock(id)
        connect(id, password).use { }
    }

    @Test
    fun `지우면 database와 계정이 모두 사라진다`() {
        val id = "db-ffffffffffff"
        provisioner.create(id, password, 10)

        provisioner.drop(id)
        provisioner.drop(id)

        assertFalse(databaseExists(id))
        assertFailsWith<SQLException> { connect(id, password) }
    }

    private fun connect(
        id: String,
        password: String,
    ) = DriverManager.getConnection(mysql.jdbcUrl, id, password)

    private fun execute(sql: String) {
        admin.connection.use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun databaseExists(id: String): Boolean =
        admin.connection.use { connection ->
            connection.prepareStatement("SELECT 1 FROM information_schema.schemata WHERE schema_name = ?").use {
                it.setString(1, id)
                it.executeQuery().use { rows -> rows.next() }
            }
        }

    private fun maxUserConnections(id: String): Int =
        admin.connection.use { connection ->
            connection.prepareStatement("SELECT max_user_connections FROM mysql.user WHERE user = ?").use {
                it.setString(1, id)
                it.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }
}
