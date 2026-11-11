package com.gsmsv.api.notification.repository

import com.gsmsv.api.notification.domain.Notification
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationRepository : JpaRepository<Notification, String> {
    fun countByUserId(userId: String): Long

    /** 오래된 것부터. 보관 상한을 넘은 알림을 지울 때 쓴다 */
    fun findByUserIdOrderByCreatedAtAsc(
        userId: String,
        pageable: Pageable,
    ): List<Notification>
}
