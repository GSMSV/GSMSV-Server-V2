package com.gsmsv.api.operation.controller

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.OperationLoader
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.operation.config.OperationProperties
import com.gsmsv.api.operation.domain.Operation
import com.gsmsv.api.operation.domain.OperationError
import com.gsmsv.api.operation.domain.OperationStage
import com.gsmsv.api.operation.domain.OperationStatus
import com.gsmsv.api.operation.domain.OperationType
import com.gsmsv.api.operation.repository.OperationRepository
import com.gsmsv.api.operation.service.OperationResourceReader
import com.gsmsv.api.operation.service.OperationStreamService
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@ApiWebMvcTest(OperationController::class)
@Import(OperationStreamService::class)
@EnableConfigurationProperties(OperationProperties::class)
class OperationControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
    @Autowired private val streamService: OperationStreamService,
) {
    @MockkBean
    private lateinit var loader: OperationLoader

    @MockkBean
    private lateinit var reader: OperationResourceReader

    @MockkBean
    private lateinit var repository: OperationRepository

    private val createdAt = Instant.parse("2026-11-12T09:00:00Z")

    private fun operation() =
        Operation(OPERATION_ID, OperationType.VM_CREATE, GRN, Owner.Team(TEAM), ME, createdAt).also {
            every { loader.load(UserActor(ME), OPERATION_ID, Action.VIEW, reader) } returns it
        }

    @Test
    fun `로더를 통과한 작업 상태를 계약 모양으로 돌려주고 빈 칸도 null로 내보낸다`() {
        val operation = operation()
        operation.report(OperationStatus.QUEUED, OperationStage.QUEUED, 2, null, createdAt)

        val body =
            mockMvc
                .get("/api/v2/operations/$OPERATION_ID") { bearer(tokens.user(ME)) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.id") { value(OPERATION_ID) }
                    jsonPath("$.type") { value("vm.create") }
                    jsonPath("$.resource.grn") { value(GRN) }
                    jsonPath("$.status") { value("queued") }
                    jsonPath("$.stage") { value("queued") }
                    jsonPath("$.queue_position") { value(2) }
                    jsonPath("$.created_at") { value("2026-11-12T09:00:00Z") }
                }.andReturn()
                .response.contentAsString

        assertContains(body, "\"error\":null")
        assertContains(body, "\"finished_at\":null")
        // Infra 작업 id는 내보내지 않는다
        assertFalse(body.contains("job"))
    }

    @Test
    fun `실패한 작업은 사용자용 원인을 준다`() {
        val operation = operation()
        operation.report(
            OperationStatus.FAILED,
            OperationStage.CLONING,
            0,
            OperationError("OPERATION_FAILED", "작업을 마치지 못했습니다.", retryable = true),
            createdAt.plusSeconds(65),
        )

        mockMvc
            .get("/api/v2/operations/$OPERATION_ID") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("failed") }
                jsonPath("$.stage") { value("cloning") }
                jsonPath("$.error.code") { value("OPERATION_FAILED") }
                jsonPath("$.error.retryable") { value(true) }
                jsonPath("$.finished_at") { value("2026-11-12T09:01:05Z") }
            }
    }

    @Test
    fun `로더가 거절하면 그 오류를 그대로 돌려준다`() {
        every { loader.load(UserActor(ME), OPERATION_ID, Action.VIEW, reader) } throws BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc
            .get("/api/v2/operations/$OPERATION_ID") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
            }
    }

    @Test
    fun `토큰이 없으면 401이다`() {
        mockMvc.get("/api/v2/operations/$OPERATION_ID").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v2/operations/$OPERATION_ID/events").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `SSE는 연결하자마자 지금 상태를 보내고 바뀌면 보낸 뒤 끝나면 닫는다`() {
        val operation = operation()
        every { repository.findAllById(setOf(OPERATION_ID)) } returns listOf(operation)

        val result =
            mockMvc
                .get("/api/v2/operations/$OPERATION_ID/events") {
                    bearer(tokens.user(ME))
                    accept = MediaType.TEXT_EVENT_STREAM
                }.andExpect { request { asyncStarted() } }
                .andReturn()

        assertEquals(1, streamService.activeCount)
        assertContains(result.response.contentAsString, "event:operation")
        assertContains(result.response.contentAsString, "\"status\":\"queued\"")

        operation.report(OperationStatus.RUNNING, OperationStage.CLONING, 0, null, createdAt.plusSeconds(10))
        streamService.push()
        assertContains(result.response.contentAsString, "\"stage\":\"cloning\"")

        operation.report(OperationStatus.SUCCEEDED, OperationStage.NETWORKING, 0, null, createdAt.plusSeconds(70))
        streamService.push()

        assertContains(result.response.contentAsString, "\"status\":\"succeeded\"")
        assertEquals(0, streamService.activeCount)
    }

    @Test
    fun `이미 끝난 작업은 SSE로 한 번 보내고 바로 닫는다`() {
        val operation = operation()
        operation.report(OperationStatus.CANCELED, OperationStage.QUEUED, 0, null, createdAt.plusSeconds(5))

        val result =
            mockMvc
                .get("/api/v2/operations/$OPERATION_ID/events") {
                    bearer(tokens.user(ME))
                    accept = MediaType.TEXT_EVENT_STREAM
                }.andReturn()

        assertContains(result.response.contentAsString, "\"status\":\"canceled\"")
        assertEquals(0, streamService.activeCount)
    }

    @Test
    fun `SSE도 스트림을 열기 전에 거절하면 JSON 오류로 답한다`() {
        every { loader.load(UserActor(ME), OPERATION_ID, Action.VIEW, reader) } throws BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc
            .get("/api/v2/operations/$OPERATION_ID/events") {
                bearer(tokens.user(ME))
                accept = MediaType.TEXT_EVENT_STREAM
            }.andExpect {
                status { isNotFound() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
            }
    }

    private companion object {
        const val ME = "user-a1b2c3d4e5f6"
        const val TEAM = "team-4p8w2k9m1xza"
        const val OPERATION_ID = "op-7w8k9q0p1a2s"
        const val GRN = "grn:$TEAM:vm-k3m9x2p7q4ta"
    }
}
