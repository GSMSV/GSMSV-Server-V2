package com.gsmsv.api.notification.controller

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.notification.dto.DiscordChannelResponse
import com.gsmsv.api.notification.dto.NotificationChannelsResponse
import com.gsmsv.api.notification.dto.NotificationSettingItemResponse
import com.gsmsv.api.notification.dto.NotificationSettingsResponse
import com.gsmsv.api.notification.dto.UpdateNotificationSettingsResponse
import com.gsmsv.api.notification.service.NotificationSettingService
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put

@ApiWebMvcTest(NotificationSettingController::class)
class NotificationSettingControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean
    private lateinit var settingService: NotificationSettingService

    @Test
    fun `내 알림 설정을 돌려준다`() {
        every { settingService.get(ME) } returns
            NotificationSettingsResponse(
                NotificationChannelsResponse("student@gsm.hs.kr", DiscordChannelResponse(false)),
                listOf(NotificationSettingItemResponse("expiry", email = true, discord = true, locked = true)),
            )

        mockMvc
            .get(URL) { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.channels.email") { value("student@gsm.hs.kr") }
                jsonPath("$.channels.discord.connected") { value(false) }
                jsonPath("$.items[0].group") { value("expiry") }
                jsonPath("$.items[0].locked") { value(true) }
            }
    }

    @Test
    fun `토큰이 없으면 401을 돌려준다`() {
        mockMvc.get(URL).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `설정을 통째로 바꾸고 바뀐 항목을 돌려준다`() {
        every { settingService.update(ME, any()) } returns
            UpdateNotificationSettingsResponse(
                listOf(NotificationSettingItemResponse("usage", email = false, discord = false, locked = false)),
            )

        mockMvc
            .put(URL) {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"items":[{"group":"usage","email":false,"discord":false}]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.items[0].group") { value("usage") }
                jsonPath("$.items[0].email") { value(false) }
            }
    }

    @Test
    fun `필수 알림을 끄려 하면 400 LOCKED_CATEGORY와 그룹 이름을 돌려준다`() {
        every { settingService.update(ME, any()) } throws
            BusinessException(ErrorCode.LOCKED_CATEGORY, mapOf("groups" to listOf("expiry")))

        mockMvc
            .put(URL) {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"items":[{"group":"expiry","email":false,"discord":false}]}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("LOCKED_CATEGORY") }
                jsonPath("$.details.groups[0]") { value("expiry") }
            }
    }

    @Test
    fun `items가 비어 있으면 400을 돌려준다`() {
        mockMvc
            .put(URL) {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"items":[]}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
    }

    companion object {
        private const val ME = "user-a1b2c3d4e5f6"
        private const val URL = "/api/v2/users/me/notification-settings"
    }
}
