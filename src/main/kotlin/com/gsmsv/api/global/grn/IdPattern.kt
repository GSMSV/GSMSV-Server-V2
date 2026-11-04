package com.gsmsv.api.global.grn

/**
 * id 무작위 부분의 정규식 조각 (Contracts-V2 `constants/resource-id.md` 1·2장).
 *
 * 글자는 `0123456789abcdefghjkmnpqrstvwxyz` 32개다 (`i`, `l`, `o`, `u` 제외).
 */
internal object IdPattern {
    private const val CHARS = "[0-9a-hjkmnp-tv-z]"

    /** 서비스 자원·사용자·팀: 12자 */
    const val LONG = "$CHARS{12}"

    /** 하위 자원: 8자 */
    const val SHORT = "$CHARS{8}"

    const val USER_PREFIX = "user"
    const val TEAM_PREFIX = "team"

    val SERVICE_PREFIXES = ResourceType.entries.joinToString("|") { it.prefix }
    val SUB_RESOURCE_PREFIXES = SubResourceType.entries.joinToString("|") { it.prefix }
}
