package com.gsmsv.api.app.controller

import com.gsmsv.api.app.dto.CreateAppRequest
import com.gsmsv.api.app.service.AppCommandService
import com.gsmsv.api.global.authorization.AuthorizationAuditLogger
import com.gsmsv.api.global.authorization.AuthorizationPolicy
import com.gsmsv.api.global.authorization.TeamLoader
import com.gsmsv.api.global.authorization.TeamMembershipReader
import com.gsmsv.api.global.authorization.TeamRole
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
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
import kotlin.test.assertEquals

class AppControllerTest {
    private val commandService = mockk<AppCommandService>()
    private val teamService = mockk<TeamService>()
    private val memberships = mockk<TeamMembershipReader>()
    private val loader = TeamLoader(AuthorizationPolicy(memberships), mockk<AuthorizationAuditLogger>(relaxed = true))
    private val controller = AppController(commandService, loader, teamService)
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

    companion object {
        private const val USER_ID = "user-a1b2c3d4e5f6"
        private const val TEAM_ID = "team-4p8w2k9m1xza"
    }
}
