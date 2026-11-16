package com.gsmsv.api.notification.domain

/**
 * 알림 세부 종류. [group]으로 묶이고, 설정은 그룹 단위로 한다.
 *
 * **(미정)** `category`→`group` 매핑 표는 계약이 제안으로만 두었다 (`openapi/monitoring.yaml`).
 * 계약이 정해지면 이 표를 함께 고친다.
 */
enum class NotificationCategory(
    val group: NotificationGroup,
    val kind: NotificationKind,
) {
    EXPIRY_7D(NotificationGroup.EXPIRY, NotificationKind.WARNING),
    EXPIRY_3D(NotificationGroup.EXPIRY, NotificationKind.WARNING),
    EXPIRY_1D(NotificationGroup.EXPIRY, NotificationKind.WARNING),
    SUSPENDED(NotificationGroup.EXPIRY, NotificationKind.WARNING),
    DELETED(NotificationGroup.EXPIRY, NotificationKind.INFO),

    DB_STORAGE_80(NotificationGroup.USAGE, NotificationKind.WARNING),
    DB_STORAGE_FULL(NotificationGroup.USAGE, NotificationKind.WARNING),

    TEAM_APPROVED(NotificationGroup.TEAM, NotificationKind.INFO),
    TEAM_REJECTED(NotificationGroup.TEAM, NotificationKind.WARNING),
    TEAM_RESOURCE_DELETED(NotificationGroup.TEAM, NotificationKind.INFO),

    RESOURCE_CREATED(NotificationGroup.RESOURCE, NotificationKind.INFO),
    RESOURCE_FAILED(NotificationGroup.RESOURCE, NotificationKind.WARNING),

    INQUIRY_REPLIED(NotificationGroup.SUPPORT, NotificationKind.INFO),
    ;

    /** 응답에서 쓰는 소문자 이름 (예: `db_storage_80`) */
    val key: String = name.lowercase()
}
