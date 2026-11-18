package com.gsmsv.api.app.controller

import com.gsmsv.api.app.domain.App
import com.gsmsv.api.app.dto.CreateAppRequest
import com.gsmsv.api.app.repository.AppResourceReader
import com.gsmsv.api.app.service.AppCommandService
import com.gsmsv.api.app.service.AppQueryService
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.AdminActor
import com.gsmsv.api.global.authorization.AuthorizationAuditLogger
import com.gsmsv.api.global.authorization.AuthorizationPolicy
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.TeamLoader
import com.gsmsv.api.global.authorization.TeamMembershipReader
import com.gsmsv.api.global.authorization.TeamRole
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.Grn
import com.gsmsv.api.global.grn.ResourceType
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.domain.TeamStatus
import com.gsmsv.api.team.service.TeamService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals

class AppControllerTest {
    private val commandService = mockk<AppCommandService>()
    private val teamService = mockk<TeamService>()
    private val memberships = mockk<TeamMembershipReader>()
    private val loader = TeamLoader(AuthorizationPolicy(memberships), mockk<AuthorizationAuditLogger>(relaxed = true))
    private val queryService = mockk<AppQueryService>()
    private val resourceReader = mockk<AppResourceReader>()
    private val resourceLoader = ServiceResourceLoader(AuthorizationPolicy(memberships), mockk(relaxed = true))
    private val controller =
        AppController(commandService, loader, teamService, queryService, resourceLoader, resourceReader)
    private val user = AuthenticatedUser(USER_ID, Role.USER)

    @Test
    fun `승인된 팀은 팀장과 DevOps가 앱을 만들 수 있다`() {
        val team = Team(TEAM_ID, "팀", TeamStatus.APPROVED)
        every { teamService.findById(TEAM_ID) } returns team
        every { commandService.createTeamApp(user, team, "앱") } returns mockk()

        for (role in listOf(TeamRole.LEADER, TeamRole.DEVOPS)) {
            every { memberships.findRole(TEAM_ID, USER_ID) } returns role
            controller.createTeamApp(user, TEAM_ID, CreateAppRequest("앱"))
        }

        verify(exactly = 2) { commandService.createTeamApp(user, team, "앱") }
    }

    @Test
    fun `일반 팀원은 앱을 만들 수 없다`() {
        every { teamService.findById(TEAM_ID) } returns Team(TEAM_ID, "팀", TeamStatus.APPROVED)
        every { memberships.findRole(TEAM_ID, USER_ID) } returns TeamRole.MEMBER

        val error = assertThrows<BusinessException> { controller.createTeamApp(user, TEAM_ID, CreateAppRequest("앱")) }
        assertEquals(ErrorCode.ACCESS_DENIED, error.errorCode)
        verify(exactly = 0) { commandService.createTeamApp(any(), any(), any()) }
    }

    @Test
    fun `승인 대기와 거절 팀은 관리자도 앱을 만들 수 없다`() {
        val admin = AuthenticatedUser(USER_ID, Role.ADMIN)
        for (status in listOf(TeamStatus.PENDING, TeamStatus.REJECTED)) {
            every { teamService.findById(TEAM_ID) } returns Team(TEAM_ID, "팀", status)
            val error = assertThrows<BusinessException> { controller.createTeamApp(admin, TEAM_ID, CreateAppRequest("앱")) }
            assertEquals(ErrorCode.TEAM_NOT_APPROVED, error.errorCode)
        }
        verify(exactly = 0) { commandService.createTeamApp(any(), any(), any()) }
    }

    @Test
    fun `팀 앱 목록은 팀원과 관리자가 조회할 수 있다`() {
        val team = Team(TEAM_ID, "팀", TeamStatus.APPROVED)
        val page = CursorPageRequest(limit = 5)
        every { teamService.findById(TEAM_ID) } returns team
        every { queryService.listTeam(any(), team, page) } returns CursorPage(emptyList(), null)

        every { memberships.findRole(TEAM_ID, USER_ID) } returns TeamRole.MEMBER
        controller.listTeamApps(user, TEAM_ID, page)
        controller.listTeamApps(AuthenticatedUser(USER_ID, Role.ADMIN), TEAM_ID, page)

        verify(exactly = 1) { queryService.listTeam(UserActor(USER_ID), team, page) }
        verify(exactly = 1) { queryService.listTeam(AdminActor(USER_ID), team, page) }
    }

    @Test
    fun `팀원이 아닌 사람의 앱 목록은 404다`() {
        every { teamService.findById(TEAM_ID) } returns Team(TEAM_ID, "팀", TeamStatus.APPROVED)
        every { memberships.findRole(TEAM_ID, USER_ID) } returns null

        val error = assertThrows<BusinessException> { controller.listTeamApps(user, TEAM_ID, CursorPageRequest()) }

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.errorCode)
        verify(exactly = 0) { queryService.listTeam(any(), any(), any()) }
    }

    @Test
    fun `개인 본인과 팀원 및 관리자는 앱 상세를 볼 수 있다`() {
        val personal = app(Owner.User(USER_ID))
        val teamApp = app(Owner.Team(TEAM_ID))
        every { resourceReader.type } returns ResourceType.APP
        every { resourceReader.findById(APP_ID) } returnsMany listOf(personal, teamApp, teamApp)
        every { queryService.get(any(), any()) } returns mockk()
        every { memberships.findRole(TEAM_ID, USER_ID) } returns TeamRole.MEMBER

        controller.getApp(user, Grn.parse("grn:$USER_ID:$APP_ID"))
        controller.getApp(user, Grn.parse("grn:$TEAM_ID:$APP_ID"))
        controller.getApp(AuthenticatedUser(USER_ID, Role.ADMIN), Grn.parse("grn:$TEAM_ID:$APP_ID"))

        verify(exactly = 1) { queryService.get(UserActor(USER_ID), personal) }
        verify(exactly = 1) { queryService.get(UserActor(USER_ID), teamApp) }
        verify(exactly = 1) { queryService.get(AdminActor(USER_ID), teamApp) }
    }

    @Test
    fun `다른 개인 소유자와 존재하지 않는 앱 상세는 404다`() {
        val other = app(Owner.User("user-aaaaaaaaaaaa"))
        every { resourceReader.type } returns ResourceType.APP
        every { resourceReader.findById(APP_ID) } returnsMany listOf(other, null)
        val grn = Grn.parse("grn:user-aaaaaaaaaaaa:$APP_ID")

        repeat(2) {
            val error = assertThrows<BusinessException> { controller.getApp(user, grn) }
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.errorCode)
        }
        verify(exactly = 0) { queryService.get(any(), any()) }
    }

    private fun app(owner: Owner) = App(APP_ID, "앱", owner, USER_ID, Instant.parse("2027-01-01T00:00:00Z"))

    companion object {
        private const val USER_ID = "user-a1b2c3d4e5f6"
        private const val TEAM_ID = "team-4p8w2k9m1xza"
        private const val APP_ID = "app-5r8t2y6w3kqm"
    }
}
