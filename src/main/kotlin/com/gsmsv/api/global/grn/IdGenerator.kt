package com.gsmsv.api.global.grn

import java.security.SecureRandom

/**
 * 새 id `{접두사}-{무작위 부분}`을 만든다 (Contracts-V2 `constants/resource-id.md` 1·2장).
 *
 * 무작위 부분은 순수한 난수다. 시각이나 순서를 담으면 만든 시각이 드러나고 다음 id를 추측할 수 있다.
 * 겹치는지는 여기서 확인하지 않는다. 저장할 때 겹치면 다시 뽑는다.
 */
object IdGenerator {
    // 계약이 암호학적 난수 생성기를 요구한다. SecureRandom은 스레드에 안전하다
    private val random = SecureRandom()

    /** 서비스 자원 id. 예: `vm-k3m9x2p7q4ta` */
    fun resourceId(type: ResourceType): String = generate(type.prefix, IdPattern.LONG_LENGTH)

    /** 하위 자원 id. 예: `snap-7qz3m8k2` */
    fun subResourceId(type: SubResourceType): String = generate(type.prefix, IdPattern.SHORT_LENGTH)

    fun userId(): String = generate(IdPattern.USER_PREFIX, IdPattern.LONG_LENGTH)

    fun teamId(): String = generate(IdPattern.TEAM_PREFIX, IdPattern.LONG_LENGTH)

    fun teamMemberId(): String = generate(IdPattern.TEAM_MEMBER_PREFIX, IdPattern.LONG_LENGTH)

    fun invitationId(): String = generate("inv", IdPattern.LONG_LENGTH)

    /** 알림 id. 하위 자원처럼 8자다 (`openapi/monitoring.yaml`). 예: `ntf-4k8m2q9t` */
    fun notificationId(): String = generate(IdPattern.NOTIFICATION_PREFIX, IdPattern.SHORT_LENGTH)

    private fun generate(
        prefix: String,
        length: Int,
    ): String {
        // 글자가 32개라 nextInt(32)는 치우침이 없다
        val chars = CharArray(length) { IdPattern.ALPHABET[random.nextInt(IdPattern.ALPHABET.length)] }
        return "$prefix-${String(chars)}"
    }
}
