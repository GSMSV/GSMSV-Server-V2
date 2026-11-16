package com.gsmsv.api.notification.dto

import com.gsmsv.api.notification.domain.Notification
import java.time.Instant

data class NotificationResourceResponse(
    val grn: String,
    val name: String,
)

data class NotificationResponse(
    val id: String,
    val kind: String,
    val category: String,
    val group: String,
    val title: String,
    val body: String,
    val resource: NotificationResourceResponse?,
    val read: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(notification: Notification) =
            NotificationResponse(
                id = notification.id,
                kind = notification.kind.name.lowercase(),
                category = notification.category.key,
                group = notification.group.key,
                title = notification.title,
                body = notification.body,
                resource = notification.resourceGrn?.let { NotificationResourceResponse(it, notification.resourceName.orEmpty()) },
                read = notification.read,
                createdAt = notification.createdAt,
            )
    }
}

data class NotificationListResponse(
    val items: List<NotificationResponse>,
    val unreadCount: Long,
)
