package com.gsmsv.api.notification.controller

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.notification.dto.NotificationListResponse
import com.gsmsv.api.notification.dto.NotificationResourceResponse
import com.gsmsv.api.notification.dto.NotificationResponse
import com.gsmsv.api.notification.service.NotificationService
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant

@ApiWebMvcTest(NotificationController::class)
class NotificationControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean
    private lateinit var notificationService: NotificationService

    @Test
    fun `내 알림 목록과 읽지 않은 개수를 돌려준다`() {
        every { notificationService.list(ME) } returns
            NotificationListResponse(
                items =
                    listOf(
                        NotificationResponse(
                            id = NOTIFICATION,
                            kind = "warning",
                            category = "db_storage_80",
                            group = "usage",
                            title = "DB 용량이 80%에 도달했습니다",
                            body = "본문",
                            resource = NotificationResourceResponse("grn:team-4p8w2k9m1xza:db-k3m9x2p7q4ta", "주문 서비스 DB"),
                            read = false,
                            createdAt = Instant.parse("2026-11-10T09:00:00Z"),
                        ),
                    ),
                unreadCount = 1,
            )

        mockMvc
            .get("/api/v2/notifications") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.unread_count") { value(1) }
                jsonPath("$.items[0].id") { value(NOTIFICATION) }
                jsonPath("$.items[0].group") { value("usage") }
                jsonPath("$.items[0].resource.name") { value("주문 서비스 DB") }
                jsonPath("$.items[0].created_at") { value("2026-11-10T09:00:00Z") }
            }
    }

    @Test
    fun `토큰이 없으면 401을 돌려준다`() {
        mockMvc.get("/api/v2/notifications").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHENTICATED") }
        }
    }

    @Test
    fun `읽음 처리는 요청자 id로 204를 돌려준다`() {
        every { notificationService.markRead(ME, NOTIFICATION) } just runs

        mockMvc
            .post("/api/v2/notifications/$NOTIFICATION/read") { bearer(tokens.user(ME)) }
            .andExpect { status { isNoContent() } }

        verify { notificationService.markRead(ME, NOTIFICATION) }
    }

    @Test
    fun `남의 알림이나 없는 알림은 404 RESOURCE_NOT_FOUND를 돌려준다`() {
        every { notificationService.markRead(ME, NOTIFICATION) } throws BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc
            .post("/api/v2/notifications/$NOTIFICATION/read") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
            }
    }

    @Test
    fun `알림 id 형식이 틀리면 DB를 보기 전에 400을 돌려준다`() {
        mockMvc
            .delete("/api/v2/notifications/ntf-short") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_RESOURCE_ID") }
            }

        verify(exactly = 0) { notificationService.delete(any(), any()) }
    }

    @Test
    fun `모두 읽음 처리는 204를 돌려준다`() {
        every { notificationService.markAllRead(ME) } just runs

        mockMvc
            .post("/api/v2/notifications/read-all") { bearer(tokens.user(ME)) }
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `삭제는 204를 돌려준다`() {
        every { notificationService.delete(ME, NOTIFICATION) } just runs

        mockMvc
            .delete("/api/v2/notifications/$NOTIFICATION") { bearer(tokens.user(ME)) }
            .andExpect { status { isNoContent() } }
    }

    companion object {
        private const val ME = "user-a1b2c3d4e5f6"
        private const val NOTIFICATION = "ntf-4k8m2q9t"
    }
}
