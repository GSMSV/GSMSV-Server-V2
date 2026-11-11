package com.gsmsv.api.database.controller

import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.dto.CreatedByResponse
import com.gsmsv.api.database.dto.DatabaseConnectionResponse
import com.gsmsv.api.database.dto.DatabaseDetailStorageResponse
import com.gsmsv.api.database.dto.DatabaseResponse
import com.gsmsv.api.database.dto.DatabaseStorageResponse
import com.gsmsv.api.database.dto.DatabaseSummaryResponse
import com.gsmsv.api.database.service.DatabaseApiService
import com.gsmsv.api.database.service.DatabaseResourceReader
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.global.response.OwnerResponse
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant
import kotlin.test.assertEquals

@ApiWebMvcTest(DatabaseController::class)
class DatabaseControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean
    private lateinit var loader: ServiceResourceLoader

    @MockkBean
    private lateinit var reader: DatabaseResourceReader

    @MockkBean
    private lateinit var databaseApiService: DatabaseApiService

    @Test
    fun `목록은 snake_case 칸으로 돌려준다`() {
        every { databaseApiService.list(UserActor(ME), null, any()) } returns
            CursorPage(
                listOf(
                    DatabaseSummaryResponse(
                        grn = GRN,
                        name = "주문 서비스 DB",
                        engine = "mysql",
                        isolation = "shared",
                        status = "active",
                        writeLocked = false,
                        storage = DatabaseStorageResponse(0.12),
                        expiresAt = Instant.parse("2027-02-28T00:00:00Z"),
                    ),
                ),
                null,
            )

        mockMvc
            .get("/api/v2/databases") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.items[0].grn") { value(GRN) }
                jsonPath("$.items[0].write_locked") { value(false) }
                jsonPath("$.items[0].storage.used_gb") { value(0.12) }
                jsonPath("$.items[0].expires_at") { value("2027-02-28T00:00:00Z") }
                jsonPath("$.next_cursor") { value(null) }
            }
    }

    @Test
    fun `owner가 me이면 요청자 개인 소유자로 좁힌다`() {
        val owner = slot<Owner>()
        every { databaseApiService.list(UserActor(ME), capture(owner), any()) } returns CursorPage(emptyList(), null)

        mockMvc.get("/api/v2/databases?owner=me") { bearer(tokens.user(ME)) }.andExpect { status { isOk() } }

        assertEquals(Owner.User(ME), owner.captured)
    }

    @Test
    fun `owner가 team 아이디이면 그 팀으로 좁히고 볼 수 없는 팀은 404다`() {
        every { databaseApiService.list(UserActor(ME), Owner.Team(TEAM), any()) } throws
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc
            .get("/api/v2/databases?owner=$TEAM") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
            }
    }

    @Test
    fun `owner 형식이 틀리면 400이다`() {
        mockMvc
            .get("/api/v2/databases?owner=nobody") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_RESOURCE_ID") }
            }
    }

    @Test
    fun `토큰이 없으면 401이다`() {
        mockMvc.get("/api/v2/databases").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `상세는 로더를 통과한 DB로 만든다`() {
        val database =
            Database.create("db-k3m9x2p7q4ta", "주문 서비스 DB", Owner.User(ME), DatabaseEngine.MYSQL, ME, Instant.parse("2027-02-28T00:00:00Z"))
        every { loader.load(UserActor(ME), GRN, Action.VIEW, reader) } returns database
        every { databaseApiService.get(UserActor(ME), database) } returns
            DatabaseResponse(
                grn = GRN,
                id = "db-k3m9x2p7q4ta",
                name = "주문 서비스 DB",
                owner = OwnerResponse.of(Owner.User(ME), "홍길동"),
                engine = "mysql",
                isolation = "shared",
                status = "active",
                writeLocked = false,
                connection = DatabaseConnectionResponse("db.example.com", 3306, "db-k3m9x2p7q4ta", "db-k3m9x2p7q4ta", true),
                storage = DatabaseDetailStorageResponse(0.12, 0.4, 1.0),
                maxConnections = 10,
                createdBy = CreatedByResponse("홍길동"),
                createdAt = Instant.parse("2026-11-01T09:00:00Z"),
                updatedAt = Instant.parse("2026-11-01T09:00:00Z"),
                expiresAt = Instant.parse("2027-02-28T00:00:00Z"),
                permissions = mapOf("delete" to true),
            )

        mockMvc
            .get("/api/v2/databases/$GRN") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.owner.type") { value("user") }
                jsonPath("$.connection.tls") { value(true) }
                jsonPath("$.connection.password") { doesNotExist() }
                jsonPath("$.storage.owner_limit_gb") { value(1.0) }
                jsonPath("$.created_by.name") { value("홍길동") }
                jsonPath("$.permissions.delete") { value(true) }
            }
    }

    @Test
    fun `GRN 형식이 틀리면 400 INVALID_GRN이다`() {
        mockMvc
            .get("/api/v2/databases/grn:$ME:db-short") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_GRN") }
            }
    }

    @Test
    fun `볼 수 없는 DB는 404다`() {
        every { loader.load(UserActor(ME), GRN, Action.VIEW, reader) } throws BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc
            .get("/api/v2/databases/$GRN") { bearer(tokens.user(ME)) }
            .andExpect { status { isNotFound() } }
    }

    companion object {
        private const val ME = "user-a1b2c3d4e5f6"
        private const val TEAM = "team-4p8w2k9m1xza"
        private const val GRN = "grn:$ME:db-k3m9x2p7q4ta"
    }
}
