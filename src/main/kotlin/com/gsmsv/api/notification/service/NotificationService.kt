package com.gsmsv.api.notification.service

import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.notification.channel.NotificationDelivery
import com.gsmsv.api.notification.domain.Notification
import com.gsmsv.api.notification.dto.NotificationListResponse
import com.gsmsv.api.notification.dto.NotificationResponse
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

    /** 최근 알림 최대 [MAX_PER_USER]건과 읽지 않은 개수. 50건 상한이라 페이지를 나누지 않는다 */
    @Transactional(readOnly = true)
    fun list(userId: String): NotificationListResponse =
        NotificationListResponse(
            items =
                notificationRepository
                    .findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, MAX_PER_USER))
                    .map(NotificationResponse::from),
            unreadCount = notificationRepository.countByUserIdAndReadFalse(userId),
        )

    /** 이미 읽은 알림이어도 성공한다. 남의 알림이나 없는 알림은 [ErrorCode.RESOURCE_NOT_FOUND] */
    @Transactional
    fun markRead(
        userId: String,
        notificationId: String,
    ) {
        findOwned(userId, notificationId).markRead()
    }

    /** 안 읽은 알림이 0건이어도 성공한다 */
    @Transactional
    fun markAllRead(userId: String) {
        notificationRepository.markAllReadByUserId(userId)
    }

    @Transactional
    fun delete(
        userId: String,
        notificationId: String,
    ) {
        notificationRepository.delete(findOwned(userId, notificationId))
    }

    // 남의 알림은 존재를 숨기려고 없는 알림과 같은 404로 답한다
    private fun findOwned(
        userId: String,
        notificationId: String,
    ): Notification =
        notificationRepository.findByIdAndUserId(notificationId, userId)
            ?: throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

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
