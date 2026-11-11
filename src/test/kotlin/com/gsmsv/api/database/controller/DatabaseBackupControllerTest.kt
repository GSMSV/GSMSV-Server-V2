package com.gsmsv.api.database.controller

import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.dto.DatabaseBackupListResponse
import com.gsmsv.api.database.dto.DatabaseBackupResponse
import com.gsmsv.api.database.service.DatabaseBackupApiService
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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant

@ApiWebMvcTest(DatabaseBackupController::class)
class DatabaseBackupControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean
    private lateinit var loader: ServiceResourceLoader

    @MockkBean
    private lateinit var reader: DatabaseResourceReader

    @MockkBean
    private lateinit var backupApiService: DatabaseBackupApiService

    @Test
    fun `로더를 통과한 DB의 백업 목록을 snake_case로 돌려준다`() {
        val database =
            Database.create("db-k3m9x2p7q4ta", "주문 서비스 DB", Owner.User(ME), DatabaseEngine.MYSQL, ME, Instant.parse("2027-02-28T00:00:00Z"))
        every { loader.load(UserActor(ME), GRN, Action.VIEW, reader) } returns database
        every { backupApiService.list(database) } returns
            DatabaseBackupListResponse(
                listOf(
                    DatabaseBackupResponse(
                        id = "backup-3x7q9m4k",
                        kind = "daily",
                        createdAt = Instant.parse("2026-11-10T03:00:00Z"),
                        sizeMb = 12.4,
                        expiresAt = Instant.parse("2026-11-17T03:00:00Z"),
                    ),
                ),
            )

        mockMvc
            .get("/api/v2/databases/$GRN/backups") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.items[0].id") { value("backup-3x7q9m4k") }
                jsonPath("$.items[0].kind") { value("daily") }
                jsonPath("$.items[0].created_at") { value("2026-11-10T03:00:00Z") }
                jsonPath("$.items[0].size_mb") { value(12.4) }
                jsonPath("$.items[0].expires_at") { value("2026-11-17T03:00:00Z") }
            }
    }

    @Test
    fun `볼 수 없는 DB는 404다`() {
        every { loader.load(UserActor(ME), GRN, Action.VIEW, reader) } throws BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc
            .get("/api/v2/databases/$GRN/backups") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
            }
    }

    @Test
    fun `GRN 형식이 틀리면 400 INVALID_GRN이다`() {
        mockMvc
            .get("/api/v2/databases/grn:$ME:db-short/backups") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_GRN") }
            }
    }

    @Test
    fun `토큰이 없으면 401이다`() {
        mockMvc.get("/api/v2/databases/$GRN/backups").andExpect { status { isUnauthorized() } }
    }

    companion object {
        private const val ME = "user-a1b2c3d4e5f6"
        private const val GRN = "grn:$ME:db-k3m9x2p7q4ta"
    }
}
