package com.gsmsv.api.notification.repository

import com.gsmsv.api.notification.domain.Notification
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NotificationRepository : JpaRepository<Notification, String> {
    fun countByUserId(userId: String): Long

    fun countByUserIdAndReadFalse(userId: String): Long

    /** 알림은 본인 것만 다루므로 id만으로 찾는 길은 두지 않는다 */
    fun findByIdAndUserId(
        id: String,
        userId: String,
    ): Notification?

    /** 오래된 것부터. 보관 상한을 넘은 알림을 지울 때 쓴다 */
    fun findByUserIdOrderByCreatedAtAsc(
        userId: String,
        pageable: Pageable,
    ): List<Notification>

    /** 최근 것부터. 같은 시각이면 id로 순서를 고정한다 */
    fun findByUserIdOrderByCreatedAtDescIdDesc(
        userId: String,
        pageable: Pageable,
    ): List<Notification>

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.userId = :userId and n.read = false")
    fun markAllReadByUserId(
        @Param("userId") userId: String,
    ): Int
}
