package com.gsmsv.api.app.service

import com.gsmsv.api.app.domain.App
import com.gsmsv.api.app.domain.AppEnvironment
import com.gsmsv.api.app.repository.AppEnvironmentRepository
import com.gsmsv.api.app.repository.AppResourceReader
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.AuthorizationPolicy
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.PermissionsResolver
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.TeamMembershipReader
import com.gsmsv.api.global.authorization.TeamRole
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.service.TeamService
import com.gsmsv.api.user.dto.UserSummary
import com.gsmsv.api.user.service.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppQueryServiceTest {
    private val loader = mockk<ServiceResourceLoader>()
    private val reader = mockk<AppResourceReader>()
    private val permissions = mockk<PermissionsResolver>()
    private val users = mockk<UserService>()
    private val teams = mockk<TeamService>()
    private val environments = mockk<AppEnvironmentRepository>()
    private val service = AppQueryService(loader, reader, permissions, users, teams, environments)
    private val actor = UserActor(USER_ID)

    @Test
    fun `개인 앱은 생성 최신 순으로 커서 페이지를 나눈다`() {
        val old = app("app-aaaaaaaaaaaa", 1)
        val middle = app("app-bbbbbbbbbbbb", 2)
        val newest = app("app-cccccccccccc", 3)
        every { loader.listVisible(actor, reader, Owner.User(USER_ID)) } returns listOf(old, newest, middle)
        every { users.findSummariesByIds(any()) } returns mapOf(USER_ID to summary())
        every { permissions.resolve(actor, any<App>(), any()) } returns permissionMap()

        val first = service.listPersonal(actor, CursorPageRequest(limit = 2))
        assertEquals(listOf(newest.id, middle.id), first.items.map { it.id })
        assertNotNull(first.nextCursor)
        assertEquals(
            "홍길동",
            first.items
                .first()
                .owner.name,
        )
        assertEquals(permissionMap(), first.items.first().permissions)

        val second = service.listPersonal(actor, CursorPageRequest(cursor = first.nextCursor, limit = 2))
        assertEquals(listOf(old.id), second.items.map { it.id })
        assertNull(second.nextCursor)
    }

    @Test
    fun `팀 앱 목록은 확인된 팀 소유자로만 조회한다`() {
        val team = Team(TEAM_ID, "개발팀")
        val app = app("app-aaaaaaaaaaaa", 1, Owner.Team(TEAM_ID))
        every { loader.listVisible(actor, reader, Owner.Team(TEAM_ID)) } returns listOf(app)
        every { users.findSummariesByIds(any()) } returns mapOf(USER_ID to summary())
        every { teams.findById(TEAM_ID) } returns team
        every { permissions.resolve(actor, app, any()) } returns permissionMap()

        val item = service.listTeam(actor, team, CursorPageRequest()).items.single()

        assertEquals("개발팀", item.owner.name)
        assertEquals("team", item.owner.type.value)
        verify(exactly = 1) { loader.listVisible(actor, reader, Owner.Team(TEAM_ID)) }
    }

    @Test
    fun `상세는 기본 환경과 빈 하위 목록 및 실제 permissions를 반환한다`() {
        val app = app("app-aaaaaaaaaaaa", 1)
        every { users.findSummariesByIds(any()) } returns mapOf(USER_ID to summary())
        every { permissions.resolve(actor, app, listOf(Action.VIEW, Action.UPDATE, Action.EXTEND, Action.DELETE)) } returns permissionMap()
        every { environments.findAllByAppId(app.id) } returns listOf(AppEnvironment("env-abcdefgh", app, "production"))

        val detail = service.get(actor, app)

        assertEquals("creating", detail.status)
        assertEquals("production", detail.envs.single().name)
        assertEquals(emptyList(), detail.services)
        assertEquals(emptyList(), detail.domains)
        assertEquals(emptyList(), detail.deployments)
        assertEquals(permissionMap(), detail.permissions)
    }

    @Test
    fun `팀원 상세 permissions는 권한 규칙표의 결과와 같다`() {
        val app = app("app-aaaaaaaaaaaa", 1, Owner.Team(TEAM_ID))
        val membership = mockk<TeamMembershipReader>()
        val policy = AuthorizationPolicy(membership)
        val query = AppQueryService(loader, reader, PermissionsResolver(policy), users, teams, environments)
        every { membership.findRole(TEAM_ID, USER_ID) } returns TeamRole.MEMBER
        every { users.findSummariesByIds(any()) } returns mapOf(USER_ID to summary())
        every { teams.findById(TEAM_ID) } returns Team(TEAM_ID, "개발팀")
        every { environments.findAllByAppId(app.id) } returns emptyList()

        val result = query.get(actor, app)

        assertEquals(mapOf("view" to true, "update" to false, "extend" to true, "delete" to false), result.permissions)
    }

    private fun app(
        id: String,
        day: Int,
        owner: Owner = Owner.User(USER_ID),
    ) = App(id, "앱", owner, USER_ID, Instant.parse("2027-01-01T00:00:00Z"), Instant.parse("2026-11-0${day}T00:00:00Z"))

    private fun summary() = UserSummary(USER_ID, "홍길동", "example@example.com")

    private fun permissionMap() = mapOf("view" to true, "update" to true, "extend" to true, "delete" to false)

    companion object {
        private const val USER_ID = "user-a1b2c3d4e5f6"
        private const val TEAM_ID = "team-4p8w2k9m1xza"
    }
}
