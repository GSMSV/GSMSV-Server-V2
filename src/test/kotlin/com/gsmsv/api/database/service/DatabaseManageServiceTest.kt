package com.gsmsv.api.database.service

import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.domain.DatabaseIsolation
import com.gsmsv.api.database.provisioner.FakeDatabaseProvisioner
import com.gsmsv.api.database.provisioner.SharedDatabaseProvisioner
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.global.authorization.Owner
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatabaseManageServiceTest {
    private val repository = mockk<DatabaseRepository>()
    private val provisioner = FakeDatabaseProvisioner()
    private val now = Instant.parse("2026-11-14T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    init {
        every { repository.saveAndFlush(any()) } answers { firstArg() }
    }

    @Test
    fun `이름을 바꾸면 앞뒤 공백을 지우고 grn과 새 이름을 돌려준다`() {
        val database = activeDatabase()

        val response = service().rename(database, "  새 이름  ")

        assertEquals("새 이름", database.name)
        assertEquals("grn:$ME:$DB_ID", response.grn)
        assertEquals("새 이름", response.name)
        verify { repository.saveAndFlush(database) }
    }

    @Test
    fun `이름은 공백만 있거나 30자를 넘으면 400 VALIDATION_FAILED이다`() {
        val database = activeDatabase()
        for (name in listOf("   ", "가".repeat(31))) {
            val error = assertFailsWith<BusinessException> { service().rename(database, name) }
            assertEquals(ErrorCode.VALIDATION_FAILED, error.errorCode)
        }
        assertEquals("주문 서비스 DB", database.name)
        service().rename(database, "가".repeat(30))
    }

    @Test
    fun `지우는 중인 DB의 이름은 바꿀 수 없다`() {
        val database = activeDatabase().apply { startDeleting() }

        val error = assertFailsWith<BusinessException> { service().rename(database, "새 이름") }

        assertEquals(ErrorCode.CONFLICT, error.errorCode)
    }

    @Test
    fun `만료 7일 전부터 14일을 만료일 뒤로 늘린다`() {
        val database = activeDatabase(expiresAt = now.plusSeconds(6 * DAY))

        val response = service().extend(database)

        assertEquals(now.plusSeconds((6 + 14) * DAY), response.expiresAt)
        assertEquals(response.expiresAt, database.expiresAt)
        verify { repository.saveAndFlush(database) }
    }

    @Test
    fun `만료 7일보다 이르면 409 EXTENSION_NOT_YET_AVAILABLE이고 바꾸지 않는다`() {
        val expiresAt = now.plusSeconds(8 * DAY)
        val database = activeDatabase(expiresAt = expiresAt)

        val error = assertFailsWith<BusinessException> { service().extend(database) }

        assertEquals(ErrorCode.EXTENSION_NOT_YET_AVAILABLE, error.errorCode)
        assertEquals(expiresAt, database.expiresAt)
        verify(exactly = 0) { repository.saveAndFlush(any()) }
    }

    @Test
    fun `정확히 만료 7일 전이면 연장할 수 있다`() {
        val database = activeDatabase(expiresAt = now.plusSeconds(7 * DAY))

        service().extend(database)

        assertEquals(now.plusSeconds(21 * DAY), database.expiresAt)
    }

    @Test
    fun `정지 유예 중에 연장하면 지금부터 14일 늘리고 계정 잠금을 푼다`() {
        val database = suspendedDatabase(expiresAt = now.minusSeconds(3 * DAY))
        provisioner.create(DB_ID, PASSWORD, 10)
        provisioner.lock(DB_ID)

        val response = service().extend(database)

        // 기준점은 지금과 기존 만료일 중 늦은 쪽이라 3일 지난 DB를 14일 연장하면 11일이 아니라 지금부터 14일이다
        assertEquals(now.plusSeconds(14 * DAY), response.expiresAt)
        assertEquals("active", database.status.name.lowercase())
        assertEquals(false, provisioner.account(DB_ID)?.locked)
    }

    @Test
    fun `정지 중이 아니면 계정 잠금을 건드리지 않는다`() {
        val database = activeDatabase(expiresAt = now.plusSeconds(DAY))

        service(provisionerAvailable = false).extend(database)

        assertEquals(now.plusSeconds(15 * DAY), database.expiresAt)
    }

    @Test
    fun `만드는 중이거나 삭제 중인 DB는 연장할 수 없다`() {
        val creating = Database.create(DB_ID, "DB", Owner.User(ME), DatabaseEngine.MYSQL, ME, now.plusSeconds(DAY))
        val deleting = activeDatabase().apply { startDeleting() }

        for (database in listOf(creating, deleting)) {
            val error = assertFailsWith<BusinessException> { service().extend(database) }
            assertEquals(ErrorCode.CONFLICT, error.errorCode)
        }
    }

    @Test
    fun `전용형은 연장할 수 없다`() {
        val dedicated =
            Database(
                id = DB_ID,
                initialName = "DB",
                ownerType = Database.OwnerType.USER,
                ownerId = ME,
                engine = DatabaseEngine.MYSQL,
                isolation = DatabaseIsolation.DEDICATED,
                createdBy = ME,
                initialExpiresAt = now.plusSeconds(DAY),
            ).apply { markActive() }

        val error = assertFailsWith<BusinessException> { service().extend(dedicated) }

        assertEquals(ErrorCode.NOT_SUPPORTED_FOR_DEDICATED, error.errorCode)
    }

    private fun service(provisionerAvailable: Boolean = true) =
        DatabaseManageService(repository, provide<SharedDatabaseProvisioner>(if (provisionerAvailable) provisioner else null), clock)

    private fun activeDatabase(expiresAt: Instant = now.plusSeconds(30 * DAY)) =
        Database.create(DB_ID, "주문 서비스 DB", Owner.User(ME), DatabaseEngine.MYSQL, ME, expiresAt).apply { markActive() }

    private fun suspendedDatabase(expiresAt: Instant) = activeDatabase(expiresAt).apply { suspend() }

    private inline fun <reified T : Any> provide(value: T?): ObjectProvider<T> =
        mockk<ObjectProvider<T>>().also { every { it.ifAvailable } returns value }

    private companion object {
        const val ME = "user-a1b2c3d4e5f6"
        const val DB_ID = "db-k3m9x2p7q4ta"
        const val PASSWORD = "Abcdefghijklmnopqrstuvwx1234"
        const val DAY = 24L * 3600
    }
}
