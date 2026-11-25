package com.gsmsv.api.team.controller

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.TeamLoader
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.quota.dto.UsageAmountResponse
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.dto.TeamAppUsageResponse
import com.gsmsv.api.team.dto.TeamDatabaseUsageResponse
import com.gsmsv.api.team.dto.TeamUsageResponse
import com.gsmsv.api.team.dto.TeamVmUsageResponse
import com.gsmsv.api.team.service.TeamApiService
import com.gsmsv.api.team.service.TeamInvitationService
import com.gsmsv.api.team.service.TeamService
import com.gsmsv.api.team.service.TeamUsageService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@ApiWebMvcTest(TeamController::class)
class TeamUsageControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean private lateinit var teamApiService: TeamApiService

    @MockkBean private lateinit var teamService: TeamService

    @MockkBean private lateinit var teamLoader: TeamLoader

    @MockkBean private lateinit var permissionsResolver: com.gsmsv.api.global.authorization.PermissionsResolver

    @MockkBean private lateinit var teamInvitationService: TeamInvitationService

    @MockkBean private lateinit var teamUsageService: TeamUsageService

    @Test
    fun `팀원은 팀 사용량과 한도를 조회한다`() {
        every { teamLoader.load(UserActor(USER_ID), TEAM_ID, Action.VIEW, teamService) } returns Team(TEAM_ID, "팀")
        every { teamUsageService.get(any()) } returns response()

        mockMvc.get(URL) { bearer(tokens.user(USER_ID)) }.andExpect {
            status { isOk() }
            jsonPath("$.team_id") { value(TEAM_ID) }
            jsonPath("$.vm.vcpu.used") { value(4) }
            jsonPath("$.db.dedicated.limit") { value(1) }
            jsonPath("$.db.write_locked") { value(true) }
            jsonPath("$.app.memory_mb.limit") { value(4096) }
            jsonPath("$.updated_at") { value(null) }
        }
        verify(exactly = 1) { teamUsageService.get(match { it.id == TEAM_ID }) }
    }

    @Test
    fun `팀원이 아니면 팀 사용량을 조회할 수 없다`() {
        every { teamLoader.load(UserActor(USER_ID), TEAM_ID, Action.VIEW, teamService) } throws
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc.get(URL) { bearer(tokens.user(USER_ID)) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
        }
    }

    private fun response() =
        TeamUsageResponse(
            TEAM_ID,
            TeamVmUsageResponse(UsageAmountResponse(4, 8), UsageAmountResponse(8, 16), UsageAmountResponse(40, 60)),
            TeamDatabaseUsageResponse(
                UsageAmountResponse(2L, 4L),
                UsageAmountResponse(1L, 1L),
                UsageAmountResponse(2.1, 2.0),
                true,
            ),
            TeamAppUsageResponse(
                UsageAmountResponse(1L, 4L),
                UsageAmountResponse(1024L, 4096L),
                UsageAmountResponse(1L, 2L),
            ),
            null,
        )

    private companion object {
        const val TEAM_ID = "team-4p8w2k9m1xza"
        const val USER_ID = "user-a1b2c3d4e5f6"
        const val URL = "/api/v2/teams/$TEAM_ID/usage"
    }
}
