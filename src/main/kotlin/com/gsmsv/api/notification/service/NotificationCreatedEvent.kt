package com.gsmsv.api.notification.service

import com.gsmsv.api.notification.channel.NotificationDelivery

/** 알림 저장이 끝났을 때 올리는 이벤트. 커밋된 뒤에 [NotificationDispatcher]가 받아 외부 채널로 보낸다 */
data class NotificationCreatedEvent(
    val deliveries: List<NotificationDelivery>,
)
