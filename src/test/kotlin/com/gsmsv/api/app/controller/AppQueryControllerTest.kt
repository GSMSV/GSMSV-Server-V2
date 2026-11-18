package com.gsmsv.api.app.controller

import com.gsmsv.api.app.repository.AppResourceReader
import com.gsmsv.api.app.service.AppCommandService
import com.gsmsv.api.app.service.AppQueryService
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.TeamLoader
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import com.gsmsv.api.team.service.TeamService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@ApiWebMvcTest(AppController::class)
class AppQueryControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean private lateinit var commands: AppCommandService

    @MockkBean private lateinit var queries: AppQueryService

    @MockkBean private lateinit var teams: TeamService

    @MockkBean private lateinit var teamLoader: TeamLoader

    @MockkBean private lateinit var resourceLoader: ServiceResourceLoader

    @MockkBean private lateinit var reader: AppResourceReader

    @Test
    fun `개인 앱 목록은 owner 없이 조회하고 인증이 필요하다`() {
        every { queries.listPersonal(UserActor(USER_ID), any()) } returns CursorPage(emptyList(), null)

        mockMvc.get("/api/v2/apps") { bearer(tokens.user(USER_ID)) }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(0) }
            jsonPath("$.next_cursor") { value(null) }
        }
        mockMvc.get("/api/v2/apps").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `볼 수 없는 팀 목록은 404다`() {
        every { teamLoader.load(UserActor(USER_ID), TEAM_ID, Action.VIEW, teams) } throws
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc.get("/api/v2/teams/$TEAM_ID/apps") { bearer(tokens.user(USER_ID)) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
        }
    }

    @Test
    fun `잘못된 팀 id와 페이지 쿼리는 400이다`() {
        every { teamLoader.load(UserActor(USER_ID), "team-bad", Action.VIEW, teams) } throws
            BusinessException(ErrorCode.INVALID_RESOURCE_ID)
        mockMvc.get("/api/v2/teams/team-bad/apps") { bearer(tokens.user(USER_ID)) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_RESOURCE_ID") }
        }
        mockMvc.get("/api/v2/apps?limit=0") { bearer(tokens.user(USER_ID)) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
        every { queries.listPersonal(UserActor(USER_ID), any()) } throws BusinessException(ErrorCode.INVALID_CURSOR)
        mockMvc.get("/api/v2/apps?cursor=bad") { bearer(tokens.user(USER_ID)) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_CURSOR") }
        }
    }

    @Test
    fun `잘못된 GRN은 400이고 보이지 않는 앱은 404다`() {
        mockMvc.get("/api/v2/apps/grn:$USER_ID:app-short") { bearer(tokens.user(USER_ID)) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_GRN") }
        }
        every { resourceLoader.load(UserActor(USER_ID), GRN, Action.VIEW, reader) } throws
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        mockMvc.get("/api/v2/apps/$GRN") { bearer(tokens.user(USER_ID)) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
        }
    }

    companion object {
        private const val USER_ID = "user-a1b2c3d4e5f6"
        private const val TEAM_ID = "team-4p8w2k9m1xza"
        private const val GRN = "grn:$USER_ID:app-5r8t2y6w3kqm"
    }
}
