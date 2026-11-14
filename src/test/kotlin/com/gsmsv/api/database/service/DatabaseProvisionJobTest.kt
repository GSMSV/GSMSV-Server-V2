package com.gsmsv.api.database.service

import com.gsmsv.api.database.crypto.DatabasePasswordCipher
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.domain.DatabaseStatus
import com.gsmsv.api.database.provisioner.FakeDatabaseProvisioner
import com.gsmsv.api.database.provisioner.SharedDatabaseProvisioner
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.notification.domain.NotificationCategory
import com.gsmsv.api.notification.service.NotificationMessage
import com.gsmsv.api.notification.service.NotificationSender
import com.gsmsv.api.operation.domain.Operation
import com.gsmsv.api.operation.domain.OperationStatus
import com.gsmsv.api.operation.repository.OperationRepository
import com.gsmsv.api.operation.service.OperationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DatabaseProvisionJobTest {
    private val repository = mockk<DatabaseRepository>()
    private val operationRepository = mockk<OperationRepository>()
    private val notifications = mockk<NotificationSender>(relaxed = true)
    private val cipher = DatabasePasswordCipher(Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() }))
    private val provisioner = FakeDatabaseProvisioner()
    private val database =
        Database.create(
            DB_ID,
            "주문 서비스 DB",
            Owner.User(ME),
            DatabaseEngine.MYSQL,
            ME,
            Instant.parse("2027-02-28T00:00:00Z"),
        )
    private val operation = Operation("op-4p8w2k9m1xza", "db.create", "db", DB_ID, ME)

    init {
        every { repository.save(any()) } answers { firstArg() }
        every { operationRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `성공하면 계정을 만들고 암호화한 비밀번호를 저장하며 active와 succeeded로 바꾼다`() {
        job().run(database, operation, PASSWORD)

        assertEquals(PASSWORD, provisioner.account(DB_ID)?.password)
        assertEquals(DatabaseStatus.ACTIVE, database.status)
        val encrypted = assertNotNull(database.passwordEncrypted)
        assertNotEquals(PASSWORD, encrypted)
        assertEquals(PASSWORD, cipher.decrypt(encrypted))
        assertEquals(OperationStatus.SUCCEEDED, operation.status)
        assertNotNull(operation.finishedAt)
    }

    @Test
    fun `성공하면 소유자에게 만들었다는 알림을 보낸다`() {
        val message = slot<NotificationMessage>()

        job().run(database, operation, PASSWORD)

        verify { notifications.send(Owner.User(ME), capture(message)) }
        assertEquals(NotificationCategory.RESOURCE_CREATED, message.captured.category)
        assertEquals("grn:$ME:$DB_ID", message.captured.resource?.grn)
    }

    @Test
    fun `공유 DB 서버가 실패하면 failed와 failed 작업으로 끝내고 비밀번호를 저장하지 않는다`() {
        provisioner.failNextCreate = true
        val message = slot<NotificationMessage>()

        job().run(database, operation, PASSWORD)

        assertEquals(DatabaseStatus.FAILED, database.status)
        assertNull(database.passwordEncrypted)
        assertNull(provisioner.account(DB_ID))
        assertEquals(OperationStatus.FAILED, operation.status)
        assertEquals("UPSTREAM_UNAVAILABLE", operation.errorCode)
        verify { notifications.send(Owner.User(ME), capture(message)) }
        assertEquals(NotificationCategory.RESOURCE_FAILED, message.captured.category)
    }

    @Test
    fun `공유 DB 서버 연결이 설정되지 않았으면 기동이 아니라 작업이 실패로 끝난다`() {
        job(provisionerAvailable = false).run(database, operation, PASSWORD)

        assertEquals(DatabaseStatus.FAILED, database.status)
        assertEquals(OperationStatus.FAILED, operation.status)
    }

    @Test
    fun `알림 발송이 실패해도 작업 결과는 그대로다`() {
        every { notifications.send(any(), any()) } throws IllegalStateException("발송 실패")

        job().run(database, operation, PASSWORD)

        assertEquals(DatabaseStatus.ACTIVE, database.status)
        assertEquals(OperationStatus.SUCCEEDED, operation.status)
    }

    private fun job(provisionerAvailable: Boolean = true) =
        DatabaseProvisionJob(
            repository,
            OperationService(operationRepository),
            provide(cipher),
            provide<SharedDatabaseProvisioner>(if (provisionerAvailable) provisioner else null),
            notifications,
        )

    private inline fun <reified T : Any> provide(value: T?): ObjectProvider<T> =
        mockk<ObjectProvider<T>>().also { every { it.ifAvailable } returns value }

    private companion object {
        const val ME = "user-a1b2c3d4e5f6"
        const val DB_ID = "db-k3m9x2p7q4ta"
        const val PASSWORD = "Abcdefghijklmnopqrstuvwx1234"
    }
}
