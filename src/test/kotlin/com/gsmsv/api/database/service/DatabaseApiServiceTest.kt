package com.gsmsv.api.database.service

import com.gsmsv.api.database.config.DatabaseProperties
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.PermissionsResolver
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.team.service.TeamService
import com.gsmsv.api.user.dto.UserSummary
import com.gsmsv.api.user.service.UserService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DatabaseApiServiceTest {
    private val loader = mockk<ServiceResourceLoader>()
    private val reader = mockk<DatabaseResourceReader>()
    private val permissionsResolver = mockk<PermissionsResolver>()
    private val userService = mockk<UserService>()
    private val teamService = mockk<TeamService>()
    private val usageReader = mockk<DatabaseUsageReader>()
    private val properties =
        DatabaseProperties(
            DatabaseProperties.Connection(host = "db.example.com"),
            DatabaseProperties.StorageLimit(personalGib = 1.0, teamGib = 2.0),
        )
    private val service =
        DatabaseApiService(loader, reader, permissionsResolver, userService, teamService, usageReader, properties)
    private val actor = UserActor(ME)

    @Test
    fun `목록은 최근 순이고 limit보다 많으면 다음 커서를 준다`() {
        val old = database("db-aaaaaaaaaaaa", Instant.parse("2026-11-01T00:00:00Z"))
        val mid = database("db-bbbbbbbbbbbb", Instant.parse("2026-11-02T00:00:00Z"))
        val recent = database("db-cccccccccccc", Instant.parse("2026-11-03T00:00:00Z"))
        every { loader.listVisible(actor, reader, null) } returns listOf(old, recent, mid)
        every { usageReader.usedGib(any()) } returns 0.12

        val page = service.list(actor, null, CursorPageRequest(limit = 2))

        assertEquals(listOf("db-cccccccccccc", "db-bbbbbbbbbbbb"), page.items.map { it.grn.substringAfterLast(':') })
        assertNotNull(page.nextCursor)

        val next = service.list(actor, null, CursorPageRequest(cursor = page.nextCursor, limit = 2))
        assertEquals(listOf("db-aaaaaaaaaaaa"), next.items.map { it.grn.substringAfterLast(':') })
        assertNull(next.nextCursor)
    }

    @Test
    fun `목록 항목은 계약 칸을 소문자 값으로 채운다`() {
        every { loader.listVisible(actor, reader, Owner.User(ME)) } returns listOf(database("db-k3m9x2p7q4ta"))
        every { usageReader.usedGib("db-k3m9x2p7q4ta") } returns 0.12

        val item = service.list(actor, Owner.User(ME), CursorPageRequest()).items.single()

        assertEquals("grn:$ME:db-k3m9x2p7q4ta", item.grn)
        assertEquals("mysql", item.engine)
        assertEquals("shared", item.isolation)
        assertEquals("creating", item.status)
        assertEquals(0.12, item.storage.usedGb)
    }

    @Test
    fun `상세는 접속 정보와 용량 한도와 permissions를 채우고 비밀번호는 없다`() {
        val database = database("db-k3m9x2p7q4ta", owner = Owner.Team(TEAM))
        every { userService.findSummariesByIds(listOf(ME)) } returns mapOf(ME to UserSummary(ME, "홍길동", "me@gsm.hs.kr"))
        every { teamService.findById(TEAM) } returns
            com.gsmsv.api.team.domain
                .Team(TEAM, "GSMSV")
        every { usageReader.usedGib("db-k3m9x2p7q4ta") } returns 0.12
        every { usageReader.ownerUsedGib(Owner.Team(TEAM)) } returns 0.4
        every { permissionsResolver.resolve(actor, database, any<Collection<Action>>()) } returns mapOf("delete" to true)

        val response = service.get(actor, database)

        assertEquals("grn:$TEAM:db-k3m9x2p7q4ta", response.grn)
        assertEquals("GSMSV", response.owner.name)
        assertEquals("홍길동", response.createdBy.name)
        assertEquals("db.example.com", response.connection.host)
        assertEquals("db-k3m9x2p7q4ta", response.connection.database)
        assertEquals("db-k3m9x2p7q4ta", response.connection.username)
        assertEquals(true, response.connection.tls)
        assertEquals(2.0, response.storage.ownerLimitGb)
        assertEquals(0.4, response.storage.ownerTotalUsedGb)
        assertEquals(mapOf("delete" to true), response.permissions)
    }

    @Test
    fun `개인 자원의 용량 한도는 개인 한도다`() {
        val database = database("db-k3m9x2p7q4ta")
        every { userService.findSummariesByIds(any()) } returns emptyMap()
        every { usageReader.usedGib(any()) } returns 0.0
        every { usageReader.ownerUsedGib(any()) } returns 0.0
        every { permissionsResolver.resolve(actor, database, any<Collection<Action>>()) } returns emptyMap()

        assertEquals(1.0, service.get(actor, database).storage.ownerLimitGb)
    }

    private fun database(
        id: String,
        createdAt: Instant = Instant.parse("2026-11-01T09:00:00Z"),
        owner: Owner = Owner.User(ME),
    ) = Database(
        id = id,
        initialName = "주문 서비스 DB",
        ownerType = if (owner is Owner.Team) Database.OwnerType.TEAM else Database.OwnerType.USER,
        ownerId = owner.id,
        engine = DatabaseEngine.MYSQL,
        createdBy = ME,
        initialExpiresAt = Instant.parse("2027-02-28T00:00:00Z"),
        initialCreatedAt = createdAt,
    )

    companion object {
        private const val ME = "user-a1b2c3d4e5f6"
        private const val TEAM = "team-4p8w2k9m1xza"
    }
}
