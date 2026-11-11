package com.gsmsv.api.notification.channel

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

// 실제 발송 구현이 들어오기 전까지 쓰는 자리다. 제목·본문·주소는 로그에 남기지 않는다
@Component
class LoggingEmailChannel : NotificationChannel {
    override val type = ChannelType.EMAIL

    override fun send(delivery: NotificationDelivery) {
        log.info { "이메일 알림 발송(로그만): userId=${delivery.userId}, category=${delivery.category.key}" }
    }
}

@Component
class LoggingDiscordChannel : NotificationChannel {
    override val type = ChannelType.DISCORD

    override fun send(delivery: NotificationDelivery) {
        log.info { "Discord 알림 발송(로그만): userId=${delivery.userId}, category=${delivery.category.key}" }
    }
}
