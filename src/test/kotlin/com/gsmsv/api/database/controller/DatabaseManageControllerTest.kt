package com.gsmsv.api.database.controller

import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.dto.DatabaseExtendResponse
import com.gsmsv.api.database.dto.RenameDatabaseResponse
import com.gsmsv.api.database.service.DatabaseManageService
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Instant

@ApiWebMvcTest(DatabaseManageController::class)
class DatabaseManageControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean
    private lateinit var loader: ServiceResourceLoader

    @MockkBean
    private lateinit var reader: DatabaseResourceReader

    @MockkBean
    private lateinit var manageService: DatabaseManageService

    private val database =
        Database.create(DB_ID, "주문 서비스 DB", Owner.User(ME), DatabaseEngine.MYSQL, ME, Instant.parse("2027-02-28T00:00:00Z"))

    @Test
    fun `이름 수정은 UPDATE로 로더를 통과하고 grn과 이름을 돌려준다`() {
        every { loader.load(UserActor(ME), GRN, Action.UPDATE, reader) } returns database
        every { manageService.rename(database, "새 이름") } returns RenameDatabaseResponse(GRN, "새 이름")

        mockMvc
            .patch("/api/v2/databases/$GRN") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"새 이름"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.grn") { value(GRN) }
                jsonPath("$.name") { value("새 이름") }
            }
    }

    @Test
    fun `권한이 없으면 403이고 이름을 바꾸지 않는다`() {
        every { loader.load(UserActor(ME), GRN, Action.UPDATE, reader) } throws BusinessException(ErrorCode.ACCESS_DENIED)

        mockMvc
            .patch("/api/v2/databases/$GRN") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"새 이름"}"""
            }.andExpect { status { isForbidden() } }
        verify(exactly = 0) { manageService.rename(any(), any()) }
    }

    @Test
    fun `이름이 비어 있으면 400이다`() {
        mockMvc
            .patch("/api/v2/databases/$GRN") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":""}"""
            }.andExpect { status { isBadRequest() } }
        verify(exactly = 0) { manageService.rename(any(), any()) }
    }

    @Test
    fun `연장은 EXTEND로 로더를 통과하고 expires_at을 돌려준다`() {
        every { loader.load(UserActor(ME), GRN, Action.EXTEND, reader) } returns database
        every { manageService.extend(database) } returns DatabaseExtendResponse(Instant.parse("2027-03-14T00:00:00Z"))

        mockMvc
            .post("/api/v2/databases/$GRN/extend") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.expires_at") { value("2027-03-14T00:00:00Z") }
            }
    }

    @Test
    fun `아직 연장할 수 없으면 409 EXTENSION_NOT_YET_AVAILABLE이다`() {
        every { loader.load(UserActor(ME), GRN, Action.EXTEND, reader) } returns database
        every { manageService.extend(database) } throws BusinessException(ErrorCode.EXTENSION_NOT_YET_AVAILABLE)

        mockMvc
            .post("/api/v2/databases/$GRN/extend") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("EXTENSION_NOT_YET_AVAILABLE") }
            }
    }

    @Test
    fun `볼 수 없는 DB의 연장은 404이고 서비스를 부르지 않는다`() {
        every { loader.load(UserActor(ME), GRN, Action.EXTEND, reader) } throws BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc
            .post("/api/v2/databases/$GRN/extend") { bearer(tokens.user(ME)) }
            .andExpect { status { isNotFound() } }
        verify(exactly = 0) { manageService.extend(any()) }
    }

    private companion object {
        const val ME = "user-a1b2c3d4e5f6"
        const val DB_ID = "db-k3m9x2p7q4ta"
        const val GRN = "grn:$ME:$DB_ID"
    }
}
