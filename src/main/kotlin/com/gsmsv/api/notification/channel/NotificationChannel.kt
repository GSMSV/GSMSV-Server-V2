package com.gsmsv.api.notification.channel

import com.gsmsv.api.notification.domain.NotificationCategory

enum class ChannelType {
    EMAIL,
    DISCORD,
}

/** 외부 채널로 나가는 알림 한 건. 웹(알림 목록)은 채널이 아니라 저장으로 처리한다 */
data class NotificationDelivery(
    val userId: String,
    val category: NotificationCategory,
    val title: String,
    val body: String,
)

/**
 * 이메일·Discord 같은 외부 발송 채널. 실제 발송은 채널마다 구현하고, 실패는 던져도 된다
 * (발송기가 잡아서 로그만 남긴다).
 */
interface NotificationChannel {
    val type: ChannelType

    fun send(delivery: NotificationDelivery)
}
