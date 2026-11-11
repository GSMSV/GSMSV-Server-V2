package com.gsmsv.api.notification.repository

import com.gsmsv.api.notification.domain.NotificationSetting
import com.gsmsv.api.notification.domain.NotificationSettingId
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationSettingRepository : JpaRepository<NotificationSetting, NotificationSettingId> {
    fun findAllByIdUserId(userId: String): List<NotificationSetting>
}
