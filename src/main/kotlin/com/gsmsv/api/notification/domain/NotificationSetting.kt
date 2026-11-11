package com.gsmsv.api.notification.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.io.Serializable

@Embeddable
data class NotificationSettingId(
    @Column(name = "user_id", length = 17, nullable = false)
    val userId: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "group_name", length = 16, nullable = false)
    val group: NotificationGroup,
) : Serializable

/** 사용자의 그룹별 채널 설정. 행이 없으면 [DEFAULT_EMAIL]·[DEFAULT_DISCORD] 기본값을 쓴다 */
@Entity
@Table(name = "notification_settings")
class NotificationSetting(
    @EmbeddedId
    val id: NotificationSettingId,
    initialEmail: Boolean,
    initialDiscord: Boolean,
) {
    @Column(nullable = false)
    final var email: Boolean = initialEmail
        private set

    @Column(nullable = false)
    final var discord: Boolean = initialDiscord
        private set

    fun change(
        email: Boolean,
        discord: Boolean,
    ) {
        this.email = email
        this.discord = discord
    }

    companion object {
        const val DEFAULT_EMAIL = true
        const val DEFAULT_DISCORD = true
    }
}
