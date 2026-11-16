package com.gsmsv.api.notification.controller

import com.gsmsv.api.global.authorization.Authenticated
import com.gsmsv.api.global.grn.IdParser
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.notification.dto.NotificationListResponse
import com.gsmsv.api.notification.service.NotificationService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 알림 확인 API (9.1~9.4). 알림은 모두 본인 것만 다루고, 남의 알림은 없는 것과 같게 404로 답한다.
 * 서비스 자원이 아니라 로더를 거치지 않으므로 `@Authenticated`로 받고 조회는 항상 요청자 id와 함께 한다.
 */
@RestController
@RequestMapping("/api/v2/notifications")
class NotificationController(
    private val notificationService: NotificationService,
) {
    @Authenticated
    @GetMapping
    fun list(
        @CurrentUser user: AuthenticatedUser,
    ): NotificationListResponse = notificationService.list(user.userId)

    @Authenticated
    @PostMapping("/{notification_id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markRead(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable("notification_id") notificationId: String,
    ) = notificationService.markRead(user.userId, IdParser.requireNotificationId(notificationId))

    @Authenticated
    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAllRead(
        @CurrentUser user: AuthenticatedUser,
    ) = notificationService.markAllRead(user.userId)

    @Authenticated
    @DeleteMapping("/{notification_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable("notification_id") notificationId: String,
    ) = notificationService.delete(user.userId, IdParser.requireNotificationId(notificationId))
}
