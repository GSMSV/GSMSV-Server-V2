package com.gsmsv.api.notification.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 사용자 한 명에게 간 알림 1건.
 *
 * 자원이 지워져도 알림에는 남아야 해서 자원 FK 없이 GRN·이름을 값으로 저장한다.
 */
@Entity
@Table(name = "notifications")
class Notification(
    @Id
    @Column(length = 12, nullable = false)
    val id: String,
    @Column(name = "user_id", length = 17, nullable = false, updatable = false)
    val userId: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    val kind: NotificationKind,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    val category: NotificationCategory,
    @Column(nullable = false, updatable = false)
    val title: String,
    @Column(nullable = false, updatable = false)
    val body: String,
    @Column(name = "resource_grn", updatable = false)
    val resourceGrn: String? = null,
    @Column(name = "resource_name", updatable = false)
    val resourceName: String? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    init {
        require((resourceGrn == null) == (resourceName == null)) { "자원 GRN과 이름은 함께 있거나 함께 없어야 합니다." }
    }

    @Column(name = "is_read", nullable = false)
    final var read: Boolean = false
        private set

    val group: NotificationGroup
        get() = category.group

    fun markRead() {
        read = true
    }
}
