package com.gsmsv.api.notification.domain

/**
 * 알림 설정 단위. 사용자는 그룹마다 이메일·Discord를 켜고 끈다.
 *
 * [required]인 그룹은 설정과 상관없이 보내고 끌 수 없다 (19번 알림).
 * **(미정)** `TEAM`·`USAGE`가 필수인지. 지금은 필수가 아닌 것으로 둔다.
 */
enum class NotificationGroup(
    val required: Boolean = false,
) {
    EXPIRY(required = true),
    USAGE,
    TEAM,
    RESOURCE,
    SUPPORT,
    ;

    /** 응답·설정에서 쓰는 소문자 이름 (예: `expiry`) */
    val key: String = name.lowercase()

    companion object {
        fun fromKey(key: String): NotificationGroup? = entries.firstOrNull { it.key == key }
    }
}
