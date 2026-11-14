package com.gsmsv.api.database.controller

import com.gsmsv.api.database.dto.CreateDatabaseResponse
import com.gsmsv.api.database.dto.CreatedDatabaseResource
import com.gsmsv.api.database.service.DatabaseProvisionService
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.TeamLoader
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.operation.dto.OperationRefResponse
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.domain.TeamStatus
import com.gsmsv.api.team.service.TeamService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@ApiWebMvcTest(DatabaseCreateController::class)
class DatabaseCreateControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean
    private lateinit var provisionService: DatabaseProvisionService

    @MockkBean
    private lateinit var teamLoader: TeamLoader

    @MockkBean
    private lateinit var teamService: TeamService

    private val team = Team(TEAM_ID, "팀", TeamStatus.APPROVED)
    private val response =
        CreateDatabaseResponse(
            OperationRefResponse("op-4p8w2k9m1xza", "db.create", "running", "running"),
            CreatedDatabaseResource("grn:$ME:db-k3m9x2p7q4ta", "creating"),
        )

    @Test
    fun `개인 DB 발급은 202와 operation, resource를 돌려주고 접속 정보는 넣지 않는다`() {
        every { provisionService.createPersonal(AuthenticatedUser(ME, Role.USER), any()) } returns response

        mockMvc
            .post("/api/v2/databases") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"주문 서비스 DB","engine":"mysql"}"""
            }.andExpect {
                status { isAccepted() }
                jsonPath("$.operation.id") { value("op-4p8w2k9m1xza") }
                jsonPath("$.operation.type") { value("db.create") }
                jsonPath("$.operation.status") { value("running") }
                jsonPath("$.resource.grn") { value("grn:$ME:db-k3m9x2p7q4ta") }
                jsonPath("$.resource.status") { value("creating") }
                jsonPath("$.password") { doesNotExist() }
                jsonPath("$.operation.password") { doesNotExist() }
            }
    }

    @Test
    fun `팀 DB 발급은 팀 로더를 CREATE로 통과한 뒤 202를 돌려준다`() {
        every { teamLoader.load(UserActor(ME), TEAM_ID, Action.CREATE, teamService) } returns team
        every { provisionService.createTeam(AuthenticatedUser(ME, Role.USER), team, any()) } returns response

        mockMvc
            .post("/api/v2/teams/$TEAM_ID/databases") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"주문 서비스 DB","engine":"mysql"}"""
            }.andExpect {
                status { isAccepted() }
                jsonPath("$.operation.type") { value("db.create") }
            }
    }

    @Test
    fun `승인되지 않은 팀은 403 TEAM_NOT_APPROVED이고 서비스를 부르지 않는다`() {
        every { teamLoader.load(UserActor(ME), TEAM_ID, Action.CREATE, teamService) } throws
            BusinessException(ErrorCode.TEAM_NOT_APPROVED)

        mockMvc
            .post("/api/v2/teams/$TEAM_ID/databases") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"DB","engine":"mysql"}"""
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("TEAM_NOT_APPROVED") }
            }
        verify(exactly = 0) { provisionService.createTeam(any(), any(), any()) }
    }

    @Test
    fun `한도 초과는 422 QUOTA_EXCEEDED로 내려간다`() {
        every { provisionService.createPersonal(any(), any()) } throws
            BusinessException(ErrorCode.QUOTA_EXCEEDED, details = mapOf("limit" to 2, "used" to 2L))

        mockMvc
            .post("/api/v2/databases") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"DB","engine":"mysql"}"""
            }.andExpect {
                status { isUnprocessableContent() }
                jsonPath("$.code") { value("QUOTA_EXCEEDED") }
                jsonPath("$.details.limit") { value(2) }
                jsonPath("$.details.used") { value(2) }
            }
    }

    @Test
    fun `엔진이 없거나 알 수 없으면 400이고 서비스를 부르지 않는다`() {
        for (body in listOf("""{"name":"DB"}""", """{"name":"DB","engine":"oracle"}""", """{"engine":"mysql"}""")) {
            mockMvc
                .post("/api/v2/databases") {
                    bearer(tokens.user(ME))
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                }.andExpect { status { isBadRequest() } }
        }
        verify(exactly = 0) { provisionService.createPersonal(any(), any()) }
    }

    @Test
    fun `토큰이 없으면 401이다`() {
        mockMvc
            .post("/api/v2/databases") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"DB","engine":"mysql"}"""
            }.andExpect { status { isUnauthorized() } }
    }

    private companion object {
        const val ME = "user-a1b2c3d4e5f6"
        const val TEAM_ID = "team-4p8w2k9m1xza"
    }
}
