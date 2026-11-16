package com.gsmsv.api.notification.service

import com.gsmsv.api.notification.channel.ChannelType
import com.gsmsv.api.notification.channel.NotificationChannel
import com.gsmsv.api.notification.channel.NotificationDelivery
import com.gsmsv.api.notification.domain.NotificationCategory
import com.gsmsv.api.notification.domain.NotificationGroup
import com.gsmsv.api.notification.domain.NotificationSetting
import com.gsmsv.api.notification.domain.NotificationSettingId
import com.gsmsv.api.notification.repository.NotificationSettingRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NotificationDispatcherTest {
    private val settingRepository = mockk<NotificationSettingRepository>()
    private val email = RecordingChannel(ChannelType.EMAIL)
    private val discord = RecordingChannel(ChannelType.DISCORD)
    private val dispatcher = NotificationDispatcher(listOf(email, discord), settingRepository)

    @Test
    fun `설정이 없으면 모든 채널로 보낸다`() {
        every { settingRepository.findAllByIdUserId(USER_ID) } returns emptyList()

        dispatcher.dispatch(event(NotificationCategory.DB_STORAGE_80))

        assertEquals(1, email.sent.size)
        assertEquals(1, discord.sent.size)
    }

    @Test
    fun `설정에서 켠 채널로만 보낸다`() {
        every { settingRepository.findAllByIdUserId(USER_ID) } returns
            listOf(setting(NotificationGroup.USAGE, email = false, discord = true))

        dispatcher.dispatch(event(NotificationCategory.DB_STORAGE_80))

        assertEquals(0, email.sent.size)
        assertEquals(1, discord.sent.size)
    }

    @Test
    fun `필수 그룹은 설정을 모두 꺼도 모든 채널로 보낸다`() {
        every { settingRepository.findAllByIdUserId(USER_ID) } returns
            listOf(setting(NotificationGroup.EXPIRY, email = false, discord = false))

        dispatcher.dispatch(event(NotificationCategory.EXPIRY_1D))

        assertEquals(1, email.sent.size)
        assertEquals(1, discord.sent.size)
    }

    @Test
    fun `한 채널이 실패해도 다른 채널은 보낸다`() {
        every { settingRepository.findAllByIdUserId(USER_ID) } returns
            listOf(setting(NotificationGroup.USAGE, email = true, discord = true))
        email.fail = true

        dispatcher.dispatch(event(NotificationCategory.DB_STORAGE_80))

        assertEquals(1, discord.sent.size)
    }

    @Test
    fun `설정을 읽지 못하면 필수 그룹만 보낸다`() {
        every { settingRepository.findAllByIdUserId(USER_ID) } throws IllegalStateException("db down")

        dispatcher.dispatch(event(NotificationCategory.EXPIRY_1D))
        dispatcher.dispatch(event(NotificationCategory.DB_STORAGE_80))

        assertEquals(1, email.sent.size)
        assertEquals(NotificationCategory.EXPIRY_1D, email.sent.single().category)
    }

    private fun event(category: NotificationCategory) =
        NotificationCreatedEvent(listOf(NotificationDelivery(USER_ID, category, "제목", "본문")))

    private fun setting(
        group: NotificationGroup,
        email: Boolean,
        discord: Boolean,
    ) = NotificationSetting(NotificationSettingId(USER_ID, group), email, discord)

    private class RecordingChannel(
        override val type: ChannelType,
    ) : NotificationChannel {
        val sent = mutableListOf<NotificationDelivery>()
        var fail = false

        override fun send(delivery: NotificationDelivery) {
            if (fail) error("발송 실패")
            sent += delivery
        }
    }

    companion object {
        private const val USER_ID = "user-a1b2c3d4e5f6"
    }
}
