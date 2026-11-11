package com.gsmsv.api.global.grn

/**
 * id 무작위 부분의 정규식 조각 (Contracts-V2 `constants/resource-id.md` 1·2장).
 *
 * 글자는 `0123456789abcdefghjkmnpqrstvwxyz` 32개다 (`i`, `l`, `o`, `u` 제외).
 */
internal object IdPattern {
    /** 무작위 부분에 쓰는 글자. [IdGenerator]가 뽑는 글자이고, 아래 정규식 문자 집합과 같아야 한다 */
    const val ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz"

    const val LONG_LENGTH = 12
    const val SHORT_LENGTH = 8

    private const val CHARS = "[0-9a-hjkmnp-tv-z]"

    /** 서비스 자원·사용자·팀: 12자 */
    const val LONG = "$CHARS{$LONG_LENGTH}"

    /** 하위 자원: 8자 */
    const val SHORT = "$CHARS{$SHORT_LENGTH}"

    const val USER_PREFIX = "user"
    const val TEAM_PREFIX = "team"
    const val TEAM_MEMBER_PREFIX = "mbr"
    const val NOTIFICATION_PREFIX = "ntf"

    val SERVICE_PREFIXES = ResourceType.entries.joinToString("|") { it.prefix }
    val SUB_RESOURCE_PREFIXES = SubResourceType.entries.joinToString("|") { it.prefix }
}
