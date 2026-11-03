package com.gsmsv.api.global.grn

/**
 * GRN에서 꺼낸 서비스 자원 id. [value]는 접두사를 포함한 전체 id다 (예: `vm-k3m9x2p7q4ta`).
 *
 * 소유자 칸 값은 일부러 담지 않는다. 권한 판단은 DB의 소유자로만 한다 (23번 규칙).
 */
data class ResourceId(
    val type: ResourceType,
    val value: String,
)
