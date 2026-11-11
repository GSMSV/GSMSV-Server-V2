package com.gsmsv.api.notification.service

import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.notification.channel.NotificationDelivery
import com.gsmsv.api.notification.domain.Notification
import com.gsmsv.api.notification.repository.NotificationRepository
import com.gsmsv.api.team.service.TeamService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val teamService: TeamService,
    private val eventPublisher: ApplicationEventPublisher,
) : NotificationSender {
    @Transactional
    override fun send(
        recipient: Owner,
        message: NotificationMessage,
    ) {
        val userIds = resolveUserIds(recipient)
        userIds.forEach { userId ->
            notificationRepository.save(toNotification(userId, message))
            trimOldest(userId)
        }
        if (userIds.isNotEmpty()) {
            // 외부 채널 발송은 커밋 뒤에 한다 (NotificationDispatcher)
            eventPublisher.publishEvent(NotificationCreatedEvent(userIds.map { toDelivery(it, message) }))
        }
    }

    private fun resolveUserIds(recipient: Owner): List<String> =
        when (recipient) {
            is Owner.User -> listOf(recipient.id)
            is Owner.Team -> teamService.findMemberUserIds(recipient.id).distinct()
        }

    private fun toNotification(
        userId: String,
        message: NotificationMessage,
    ) = Notification(
        id = IdGenerator.notificationId(),
        userId = userId,
        kind = message.category.kind,
        category = message.category,
        title = message.title,
        body = message.body,
        resourceGrn = message.resource?.grn,
        resourceName = message.resource?.name,
    )

    private fun toDelivery(
        userId: String,
        message: NotificationMessage,
    ) = NotificationDelivery(userId, message.category, message.title, message.body)

    // 사용자당 보관 상한을 넘으면 오래된 것부터 지운다 (9.1)
    private fun trimOldest(userId: String) {
        val excess = notificationRepository.countByUserId(userId) - MAX_PER_USER
        if (excess > 0) {
            notificationRepository.deleteAll(
                notificationRepository.findByUserIdOrderByCreatedAtAsc(userId, PageRequest.of(0, excess.toInt())),
            )
        }
    }

    companion object {
        const val MAX_PER_USER = 50
    }
}
