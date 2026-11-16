package com.gsmsv.api.database.service

import com.gsmsv.api.audit.domain.AuditEventType
import com.gsmsv.api.audit.service.AuditRecorder
import com.gsmsv.api.database.config.DatabaseProperties
import com.gsmsv.api.database.crypto.DatabasePasswordCipher
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.domain.DatabaseIsolation
import com.gsmsv.api.database.provisioner.FakeDatabaseProvisioner
import com.gsmsv.api.database.provisioner.SharedDatabaseProvisioner
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class DatabaseCredentialServiceTest {
    private val repository = mockk<DatabaseRepository>()
    private val auditRecorder = mockk<AuditRecorder>(relaxed = true)
    private val cipher = DatabasePasswordCipher(Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() }))
    private val provisioner = FakeDatabaseProvisioner()
    private val properties =
        DatabaseProperties(
            DatabaseProperties.Connection(host = "db.example.com"),
            DatabaseProperties.StorageLimit(personalGib = 1.0, teamGib = 2.0),
        )
    private val clock = Clock.fixed(Instant.parse("2026-11-10T09:00:00Z"), ZoneOffset.UTC)
    private val service =
        DatabaseCredentialService(
            repository,
            provide(cipher),
            provide<SharedDatabaseProvisioner>(provisioner),
            auditRecorder,
            properties,
            clock,
        )
    private val actor = UserActor(ME)

    init {
        every { repository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `접속 정보는 저장된 비밀번호를 풀어 주고 감사 로그를 남긴다`() {
        val database = activeDatabase().apply { changePassword(cipher.encrypt("old-password")) }

        val response = service.credentials(actor, database)

        assertEquals("db.example.com", response.host)
        assertEquals(3306, response.port)
        assertEquals(DB_ID, response.database)
        assertEquals(DB_ID, response.username)
        assertEquals("old-password", response.password)
        assertEquals("jdbc:mysql://db.example.com:3306/$DB_ID?sslMode=REQUIRED", response.jdbcUrl)
        verify(exactly = 1) { auditRecorder.record(actor, AuditEventType.DATABASE_CREDENTIALS_READ, GRN, emptyMap()) }
    }

    @Test
    fun `볼 때마다 감사 로그를 남긴다`() {
        val database = activeDatabase().apply { changePassword(cipher.encrypt("old-password")) }

        service.credentials(actor, database)
        service.credentials(actor, database)

        verify(exactly = 2) { auditRecorder.record(actor, AuditEventType.DATABASE_CREDENTIALS_READ, GRN, any()) }
    }

    @Test
    fun `감사 로그 기록이 실패하면 비밀번호를 내주지 않는다`() {
        val database = activeDatabase().apply { changePassword(cipher.encrypt("old-password")) }
        every { auditRecorder.record(any(), any(), any(), any()) } throws IllegalStateException("기록 실패")

        assertFailsWith<IllegalStateException> { service.credentials(actor, database) }
    }

    @Test
    fun `비밀번호가 아직 없으면 409 CONFLICT이고 감사 로그를 남기지 않는다`() {
        val error = assertFailsWith<BusinessException> { service.credentials(actor, activeDatabase()) }

        assertEquals(ErrorCode.CONFLICT, error.errorCode)
        verify(exactly = 0) { auditRecorder.record(any(), any(), any(), any()) }
    }

    @Test
    fun `비밀번호를 교체하면 서버 계정과 저장된 값이 새 비밀번호로 바뀌고 감사 로그를 남긴다`() {
        val database = activeDatabase().apply { changePassword(cipher.encrypt("old-password")) }
        provisioner.create(DB_ID, "OldPassword0123456789", Database.DEFAULT_MAX_CONNECTIONS)

        val response = service.rotate(actor, database)

        assertNotEquals("OldPassword0123456789", response.password)
        assertEquals(response.password, provisioner.account(DB_ID)?.password)
        assertEquals(response.password, cipher.decrypt(checkNotNull(database.passwordEncrypted)))
        assertEquals(Instant.parse("2026-11-10T09:00:00Z"), response.rotatedAt)
        assertEquals(0, response.reconnected.functions)
        assertEquals(0, response.reconnected.appCells)
        verify(exactly = 1) { repository.save(database) }
        verify(exactly = 1) { auditRecorder.record(actor, AuditEventType.DATABASE_PASSWORD_ROTATE, GRN, emptyMap()) }
    }

    @Test
    fun `전용형은 409 NOT_SUPPORTED_FOR_DEDICATED이고 아무것도 바꾸지 않는다`() {
        val database =
            Database(
                id = DB_ID,
                initialName = "전용 DB",
                ownerType = Database.OwnerType.USER,
                ownerId = ME,
                engine = DatabaseEngine.MYSQL,
                isolation = DatabaseIsolation.DEDICATED,
                createdBy = ME,
                initialExpiresAt = Instant.parse("2027-02-28T00:00:00Z"),
            ).apply { markActive() }

        val error = assertFailsWith<BusinessException> { service.rotate(actor, database) }

        assertEquals(ErrorCode.NOT_SUPPORTED_FOR_DEDICATED, error.errorCode)
        verify(exactly = 0) { repository.save(any()) }
        verify(exactly = 0) { auditRecorder.record(any(), any(), any(), any()) }
    }

    @Test
    fun `만드는 중인 DB는 교체할 수 없다`() {
        val database = newDatabase()

        val error = assertFailsWith<BusinessException> { service.rotate(actor, database) }

        assertEquals(ErrorCode.CONFLICT, error.errorCode)
    }

    @Test
    fun `공유 DB 서버 교체가 실패하면 예외를 던진다`() {
        // 서버에 계정이 없으면 모의 구현이 실패한다. 예외가 트랜잭션을 되돌린다
        val database = activeDatabase().apply { changePassword(cipher.encrypt("old-password")) }

        assertFailsWith<RuntimeException> { service.rotate(actor, database) }
    }

    private fun newDatabase(): Database =
        Database.create(DB_ID, "주문 서비스 DB", Owner.User(ME), DatabaseEngine.MYSQL, ME, Instant.parse("2027-02-28T00:00:00Z"))

    private fun activeDatabase(): Database = newDatabase().apply { markActive() }

    private inline fun <reified T : Any> provide(value: T): ObjectProvider<T> =
        mockk<ObjectProvider<T>> { every { ifAvailable } returns value }

    companion object {
        private const val ME = "user-a1b2c3d4e5f6"
        private const val DB_ID = "db-k3m9x2p7q4ta"
        private const val GRN = "grn:$ME:$DB_ID"
    }
}
