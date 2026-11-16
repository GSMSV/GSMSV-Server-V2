package com.gsmsv.api.database.service

import com.gsmsv.api.database.config.DatabaseProperties
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.dto.CreateDatabaseRequest
import com.gsmsv.api.database.repository.DatabaseQuotaLock
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.operation.domain.Operation
import com.gsmsv.api.operation.service.OperationExecutor
import com.gsmsv.api.operation.service.OperationService
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.domain.TeamStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseProvisionServiceTest {
    private val repository = mockk<DatabaseRepository>()
    private val quotaLock = mockk<DatabaseQuotaLock>(relaxed = true)
    private val operations = mockk<OperationService>()
    private val job = mockk<DatabaseProvisionJob>(relaxed = true)
    private val executed = mutableListOf<Runnable>()
    private val executor = OperationExecutor { executed += it }
    private val properties =
        DatabaseProperties(
            DatabaseProperties.Connection(host = "db.example.com"),
            DatabaseProperties.StorageLimit(personalGib = 1.0, teamGib = 2.0),
            DatabaseProperties.CountLimit(personal = 2, team = 4),
        )
    private val clock = Clock.fixed(Instant.parse("2026-11-14T09:00:00Z"), ZoneOffset.UTC)
    private val service = DatabaseProvisionService(repository, quotaLock, operations, job, executor, properties, clock)
    private val user = AuthenticatedUser(ME, Role.USER)
    private val request = CreateDatabaseRequest("주문 서비스 DB", "mysql")

    init {
        every { repository.existsByOwnerTypeAndOwnerIdAndWriteLockedTrueAndDeletedAtIsNull(any(), any()) } returns false
        every { repository.countByOwnerTypeAndOwnerIdAndDeletedAtIsNull(any(), any()) } returns 0
        every { repository.save(any()) } answers { firstArg() }
        every { operations.register(any(), any(), any(), any()) } answers {
            Operation("op-4p8w2k9m1xza", firstArg(), secondArg(), thirdArg(), arg(3))
        }
    }

    @Test
    fun `개인 DB는 creating 상태로 저장하고 running 작업과 함께 돌려준다`() {
        val saved = slot<Database>()
        every { repository.save(capture(saved)) } answers { firstArg() }

        val response = service.createPersonal(user, request)

        assertEquals("creating", response.resource.status)
        assertEquals("grn:$ME:${saved.captured.id}", response.resource.grn)
        assertEquals("op-4p8w2k9m1xza", response.operation.id)
        assertEquals("db.create", response.operation.type)
        assertEquals("running", response.operation.status)
        assertEquals(Owner.User(ME), saved.captured.owner)
        assertEquals(DatabaseEngine.MYSQL, saved.captured.engine)
        assertEquals(
            "creating",
            saved.captured.status.name
                .lowercase(),
        )
        assertEquals(Instant.parse("2026-11-28T09:00:00Z"), saved.captured.expiresAt)
        verify { operations.register("db.create", "db", saved.captured.id, ME) }
    }

    @Test
    fun `이름 앞뒤 공백은 지우고 저장한다`() {
        val saved = slot<Database>()
        every { repository.save(capture(saved)) } answers { firstArg() }

        service.createPersonal(user, CreateDatabaseRequest("  주문 DB  ", "mysql"))

        assertEquals("주문 DB", saved.captured.name)
    }

    @Test
    fun `공유 DB 서버에 만드는 일은 요청 안이 아니라 실행기로 넘긴다`() {
        service.createPersonal(user, request)

        verify(exactly = 0) { job.run(any(), any(), any()) }
        assertEquals(1, executed.size)

        executed.single().run()
        verify { job.run(any(), any(), any()) }
    }

    @Test
    fun `한도에 닿으면 422 QUOTA_EXCEEDED이고 한도와 쓰는 개수를 알려 준다`() {
        every { repository.countByOwnerTypeAndOwnerIdAndDeletedAtIsNull(Database.OwnerType.USER, ME) } returns 2

        val error = assertFailsWith<BusinessException> { service.createPersonal(user, request) }

        assertEquals(ErrorCode.QUOTA_EXCEEDED, error.errorCode)
        // 숫자 종류(Int·Long)와 상관없이 값만 비교한다
        assertEquals(listOf(2L, 2L), listOf("limit", "used").map { (error.details?.get(it) as Number).toLong() })
        verify(exactly = 0) { repository.save(any()) }
        assertTrue(executed.isEmpty())
    }

    @Test
    fun `쓰기가 막힌 소유자는 422 WRITE_LOCKED_OWNER이다`() {
        every { repository.existsByOwnerTypeAndOwnerIdAndWriteLockedTrueAndDeletedAtIsNull(Database.OwnerType.USER, ME) } returns true

        val error = assertFailsWith<BusinessException> { service.createPersonal(user, request) }

        assertEquals(ErrorCode.WRITE_LOCKED_OWNER, error.errorCode)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `개수 검사 전에 소유자 잠금을 먼저 잡는다`() {
        service.createPersonal(user, request)

        verify { quotaLock.lock(Database.OwnerType.USER, ME) }
    }

    @Test
    fun `팀 DB는 팀 앞으로 만들고 팀 한도 4개와 31일 기간을 쓴다`() {
        val team = Team(TEAM_ID, "팀", TeamStatus.APPROVED)
        val saved = slot<Database>()
        every { repository.save(capture(saved)) } answers { firstArg() }
        every { repository.countByOwnerTypeAndOwnerIdAndDeletedAtIsNull(Database.OwnerType.TEAM, TEAM_ID) } returns 3

        val response = service.createTeam(user, team, request)

        assertEquals(Owner.Team(TEAM_ID), saved.captured.owner)
        assertEquals("grn:$TEAM_ID:${saved.captured.id}", response.resource.grn)
        assertEquals(Instant.parse("2026-12-15T09:00:00Z"), saved.captured.expiresAt)
        verify { quotaLock.lock(Database.OwnerType.TEAM, TEAM_ID) }
    }

    @Test
    fun `팀이 4개를 가지고 있으면 팀 DB도 한도 초과다`() {
        every { repository.countByOwnerTypeAndOwnerIdAndDeletedAtIsNull(Database.OwnerType.TEAM, TEAM_ID) } returns 4

        val error = assertFailsWith<BusinessException> { service.createTeam(user, Team(TEAM_ID, "팀", TeamStatus.APPROVED), request) }

        assertEquals(ErrorCode.QUOTA_EXCEEDED, error.errorCode)
    }

    @Test
    fun `관리자의 개인 DB는 개인 한도를 그대로 쓰되 기간은 31일이다`() {
        val saved = slot<Database>()
        every { repository.save(capture(saved)) } answers { firstArg() }

        service.createPersonal(AuthenticatedUser(ME, Role.ADMIN), request)

        assertEquals(Instant.parse("2026-12-15T09:00:00Z"), saved.captured.expiresAt)
    }

    private companion object {
        const val ME = "user-a1b2c3d4e5f6"
        const val TEAM_ID = "team-4p8w2k9m1xza"
    }
}
