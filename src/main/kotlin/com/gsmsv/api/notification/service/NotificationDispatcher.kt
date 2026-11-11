package com.gsmsv.api.notification.service

import com.gsmsv.api.notification.channel.ChannelType
import com.gsmsv.api.notification.channel.NotificationChannel
import com.gsmsv.api.notification.channel.NotificationDelivery
import com.gsmsv.api.notification.domain.NotificationGroup
import com.gsmsv.api.notification.domain.NotificationSetting
import com.gsmsv.api.notification.repository.NotificationSettingRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private val log = KotlinLogging.logger {}

/**
 * 알림이 커밋된 뒤 사용자 설정을 보고 이메일·Discord로 보낸다.
 * 채널 하나가 실패해도 다른 채널과 호출한 도메인 동작에는 영향이 없도록 로그만 남긴다.
 */
@Component
class NotificationDispatcher(
    private val channels: List<NotificationChannel>,
    private val settingRepository: NotificationSettingRepository,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun dispatch(event: NotificationCreatedEvent) {
        event.deliveries.groupBy { it.userId }.forEach { (userId, deliveries) ->
            val settings = loadSettings(userId) ?: return@forEach
            deliveries.forEach { delivery -> sendThroughChannels(delivery, settings) }
        }
    }

    private fun loadSettings(userId: String): Map<NotificationGroup, NotificationSetting>? =
        try {
            settingRepository.findAllByIdUserId(userId).associateBy { it.id.group }
        } catch (e: Exception) {
            log.warn(e) { "알림 설정을 읽지 못해 외부 채널 발송을 건너뜁니다: userId=$userId" }
            null
        }

    private fun sendThroughChannels(
        delivery: NotificationDelivery,
        settings: Map<NotificationGroup, NotificationSetting>,
    ) {
        val group = delivery.category.group
        val setting = settings[group]
        channels.forEach { channel ->
            if (!enabled(channel.type, group.required, setting)) return@forEach
            try {
                channel.send(delivery)
            } catch (e: Exception) {
                log.warn(e) { "알림 채널 발송에 실패했습니다: channel=${channel.type}, userId=${delivery.userId}" }
            }
        }
    }

    // 필수 그룹은 설정과 상관없이 보낸다. 연결 여부 같은 채널 쪽 제약은 채널 구현이 본다
    private fun enabled(
        type: ChannelType,
        required: Boolean,
        setting: NotificationSetting?,
    ): Boolean =
        required ||
            when (type) {
                ChannelType.EMAIL -> setting?.email ?: NotificationSetting.DEFAULT_EMAIL
                ChannelType.DISCORD -> setting?.discord ?: NotificationSetting.DEFAULT_DISCORD
            }
}
