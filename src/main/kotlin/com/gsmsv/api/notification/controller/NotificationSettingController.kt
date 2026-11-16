package com.gsmsv.api.notification.controller

import com.gsmsv.api.global.authorization.Authenticated
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.notification.dto.NotificationSettingsResponse
import com.gsmsv.api.notification.dto.UpdateNotificationSettingsRequest
import com.gsmsv.api.notification.dto.UpdateNotificationSettingsResponse
import com.gsmsv.api.notification.service.NotificationSettingService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 알림 설정 API (9.5·9.6). 본인 설정만 다루므로 `@Authenticated`로 받고 요청자 id로만 조회한다 */
@RestController
@RequestMapping("/api/v2/users/me/notification-settings")
class NotificationSettingController(
    private val settingService: NotificationSettingService,
) {
    @Authenticated
    @GetMapping
    fun get(
        @CurrentUser user: AuthenticatedUser,
    ): NotificationSettingsResponse = settingService.get(user.userId)

    @Authenticated
    @PutMapping
    fun update(
        @CurrentUser user: AuthenticatedUser,
        @Valid @RequestBody request: UpdateNotificationSettingsRequest,
    ): UpdateNotificationSettingsResponse = settingService.update(user.userId, request.items)
}
