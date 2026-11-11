package com.gsmsv.api.notification.service

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.notification.domain.NotificationGroup
import com.gsmsv.api.notification.domain.NotificationSetting
import com.gsmsv.api.notification.domain.NotificationSettingId
import com.gsmsv.api.notification.dto.UpdateNotificationSettingItem
import com.gsmsv.api.notification.repository.NotificationSettingRepository
import com.gsmsv.api.user.dto.UserSummary
import com.gsmsv.api.user.service.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationSettingServiceTest {
    private val settingRepository = mockk<NotificationSettingRepository>(relaxed = true)
    private val userService = mockk<UserService>()
    private val service = NotificationSettingService(settingRepository, userService)

    @Test
    fun `설정이 없으면 모두 켠 기본값을 보여 주고 필수 그룹만 locked다`() {
        every { settingRepository.findAllByIdUserId(USER_ID) } returns emptyList()
        every { userService.findSummariesByIds(listOf(USER_ID)) } returns
            mapOf(USER_ID to UserSummary(USER_ID, "홍길동", "student@gsm.hs.kr"))

        val response = service.get(USER_ID)

        assertEquals("student@gsm.hs.kr", response.channels.email)
        assertFalse(response.channels.discord.connected)
        assertEquals(NotificationGroup.entries.map { it.key }, response.items.map { it.group })
        assertTrue(response.items.all { it.email && it.discord })
        assertEquals(listOf("expiry"), response.items.filter { it.locked }.map { it.group })
    }

    @Test
    fun `저장된 설정을 그룹에 반영해 보여 준다`() {
        every { settingRepository.findAllByIdUserId(USER_ID) } returns
            listOf(NotificationSetting(NotificationSettingId(USER_ID, NotificationGroup.USAGE), false, true))
        every { userService.findSummariesByIds(listOf(USER_ID)) } returns emptyMap()

        val usage = service.get(USER_ID).items.single { it.group == "usage" }

        assertFalse(usage.email)
        assertTrue(usage.discord)
    }

    @Test
    fun `처음 바꾸면 설정 행을 만들고 이미 있으면 값을 바꾼다`() {
        val existing = NotificationSetting(NotificationSettingId(USER_ID, NotificationGroup.TEAM), true, true)
        every { settingRepository.findAllByIdUserId(USER_ID) } returns listOf(existing)
        val saved = slot<NotificationSetting>()
        every { settingRepository.save(capture(saved)) } answers { saved.captured }

        service.update(
            USER_ID,
            listOf(
                UpdateNotificationSettingItem("usage", email = false, discord = false),
                UpdateNotificationSettingItem("team", email = false, discord = true),
            ),
        )

        assertEquals(NotificationGroup.USAGE, saved.captured.id.group)
        assertFalse(saved.captured.email)
        assertFalse(existing.email)
        assertTrue(existing.discord)
    }

    @Test
    fun `필수 알림을 끄려 하면 아무것도 바꾸지 않고 LOCKED_CATEGORY로 거절한다`() {
        val exception =
            assertFailsWith<BusinessException> {
                service.update(
                    USER_ID,
                    listOf(
                        UpdateNotificationSettingItem("usage", email = false, discord = false),
                        UpdateNotificationSettingItem("expiry", email = false, discord = true),
                    ),
                )
            }

        assertEquals(ErrorCode.LOCKED_CATEGORY, exception.errorCode)
        assertEquals(mapOf("groups" to listOf("expiry")), exception.details)
        verify(exactly = 0) { settingRepository.save(any()) }
    }

    @Test
    fun `필수 알림을 켠 값 그대로 보내는 것은 허용한다`() {
        every { settingRepository.findAllByIdUserId(USER_ID) } returns emptyList()
        every { settingRepository.save(any()) } answers { firstArg() }

        service.update(USER_ID, listOf(UpdateNotificationSettingItem("expiry", email = true, discord = true)))

        verify { settingRepository.save(any()) }
    }

    @Test
    fun `없는 그룹이나 중복된 그룹은 VALIDATION_FAILED다`() {
        listOf(
            listOf(UpdateNotificationSettingItem("nope", email = true, discord = true)),
            listOf(
                UpdateNotificationSettingItem("usage", email = true, discord = true),
                UpdateNotificationSettingItem("usage", email = false, discord = false),
            ),
        ).forEach { items ->
            assertEquals(ErrorCode.VALIDATION_FAILED, assertFailsWith<BusinessException> { service.update(USER_ID, items) }.errorCode)
        }
    }

    companion object {
        private const val USER_ID = "user-a1b2c3d4e5f6"
    }
}
