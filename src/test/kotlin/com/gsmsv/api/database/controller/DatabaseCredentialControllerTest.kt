package com.gsmsv.api.database.controller

import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.dto.DatabaseCredentialResponse
import com.gsmsv.api.database.dto.PasswordRotationResponse
import com.gsmsv.api.database.dto.ReconnectedResponse
import com.gsmsv.api.database.service.DatabaseCredentialService
import com.gsmsv.api.database.service.DatabaseResourceReader
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant

@ApiWebMvcTest(DatabaseCredentialController::class)
class DatabaseCredentialControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean
    private lateinit var loader: ServiceResourceLoader

    @MockkBean
    private lateinit var reader: DatabaseResourceReader

    @MockkBean
    private lateinit var credentialService: DatabaseCredentialService

    private val database =
        Database.create(DB_ID, "주문 서비스 DB", Owner.User(ME), DatabaseEngine.MYSQL, ME, Instant.parse("2027-02-28T00:00:00Z"))

    @Test
    fun `접속 정보는 VIEW_ACCESS_INFO로 로더를 통과하고 no-store로 돌려준다`() {
        every { loader.load(UserActor(ME), GRN, Action.VIEW_ACCESS_INFO, reader) } returns database
        every { credentialService.credentials(UserActor(ME), database) } returns
            DatabaseCredentialResponse(
                host = "db.example.com",
                port = 3306,
                database = DB_ID,
                username = DB_ID,
                password = "example-password-not-real",
                jdbcUrl = "jdbc:mysql://db.example.com:3306/$DB_ID?sslMode=REQUIRED",
            )

        mockMvc
            .get("/api/v2/databases/$GRN/credentials") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                header { string("Cache-Control", "no-store") }
                jsonPath("$.host") { value("db.example.com") }
                jsonPath("$.port") { value(3306) }
                jsonPath("$.username") { value(DB_ID) }
                jsonPath("$.password") { value("example-password-not-real") }
                jsonPath("$.jdbc_url") { value("jdbc:mysql://db.example.com:3306/$DB_ID?sslMode=REQUIRED") }
            }
    }

    @Test
    fun `볼 수 없는 DB의 접속 정보는 404이고 서비스를 부르지 않는다`() {
        every { loader.load(UserActor(ME), GRN, Action.VIEW_ACCESS_INFO, reader) } throws
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc
            .get("/api/v2/databases/$GRN/credentials") { bearer(tokens.user(ME)) }
            .andExpect { status { isNotFound() } }

        verify(exactly = 0) { credentialService.credentials(any(), any()) }
    }

    @Test
    fun `토큰이 없으면 접속 정보는 401이다`() {
        mockMvc.get("/api/v2/databases/$GRN/credentials").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `GRN 형식이 틀리면 400 INVALID_GRN이다`() {
        mockMvc
            .get("/api/v2/databases/grn:$ME:db-short/credentials") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_GRN") }
            }
    }

    @Test
    fun `비밀번호 교체는 ROTATE_PASSWORD로 로더를 통과하고 새 비밀번호를 돌려준다`() {
        every { loader.load(UserActor(ME), GRN, Action.ROTATE_PASSWORD, reader) } returns database
        every { credentialService.rotate(UserActor(ME), database) } returns
            PasswordRotationResponse(
                password = "example-new-password-not-real",
                rotatedAt = Instant.parse("2026-11-10T09:00:00Z"),
                reconnected = ReconnectedResponse(functions = 0, appCells = 0),
            )

        mockMvc
            .post("/api/v2/databases/$GRN/password-rotation") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                header { string("Cache-Control", "no-store") }
                jsonPath("$.password") { value("example-new-password-not-real") }
                jsonPath("$.rotated_at") { value("2026-11-10T09:00:00Z") }
                jsonPath("$.reconnected.functions") { value(0) }
                jsonPath("$.reconnected.app_cells") { value(0) }
            }
    }

    @Test
    fun `일반 팀원이 교체하면 403이고 서비스를 부르지 않는다`() {
        every { loader.load(UserActor(ME), GRN, Action.ROTATE_PASSWORD, reader) } throws
            BusinessException(ErrorCode.ACCESS_DENIED)

        mockMvc
            .post("/api/v2/databases/$GRN/password-rotation") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("ACCESS_DENIED") }
            }

        verify(exactly = 0) { credentialService.rotate(any(), any()) }
    }

    @Test
    fun `전용형 교체는 409 NOT_SUPPORTED_FOR_DEDICATED다`() {
        every { loader.load(UserActor(ME), GRN, Action.ROTATE_PASSWORD, reader) } returns database
        every { credentialService.rotate(UserActor(ME), database) } throws
            BusinessException(ErrorCode.NOT_SUPPORTED_FOR_DEDICATED)

        mockMvc
            .post("/api/v2/databases/$GRN/password-rotation") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("NOT_SUPPORTED_FOR_DEDICATED") }
            }
    }

    companion object {
        private const val ME = "user-a1b2c3d4e5f6"
        private const val DB_ID = "db-k3m9x2p7q4ta"
        private const val GRN = "grn:$ME:$DB_ID"
    }
}
