package com.gsmsv.api.global.authorization

/**
 * 플랫폼 전체가 대상인 관리자 전용 동작의 판단 지점 (관리자 인프라 현황 11.13~11.15 등).
 *
 * 특정 자원이 없으므로 자원을 꺼내지 않고 판단만 한다. 다른 로더와 같은 [AuthorizationPolicy]를 지나고,
 * 관리자 통과를 포함한 모든 판단을 감사 로그로 남긴다. 관리자라고 따로 건너뛰는 길은 두지 않는다 (22번 6장).
 */
class PlatformLoader(
    private val policy: AuthorizationPolicy,
    private val auditLogger: AuthorizationAuditLogger,
) {
    /**
     * @throws com.gsmsv.api.global.exception.BusinessException 관리자가 아니면 403 `ACCESS_DENIED`
     * @throws IllegalStateException 요청 안에서 엔드포인트의 [RequirePermission]과 다른 동작을 넘기면
     * @throws IllegalArgumentException 관리자 전용이 아닌 동작을 넘기면
     */
    fun authorize(
        actor: Actor,
        action: Action,
    ) {
        DeclaredPermission.verify(action)
        val decision = policy.decidePlatform(actor, action)
        auditLogger.record(actor, action, TARGET, decision)
        decision.throwIfDenied()
    }

    private companion object {
        /** 감사 로그의 대상 칸. 자원 id가 없으므로 플랫폼 전체를 뜻하는 값을 쓴다 */
        const val TARGET = "platform"
    }
}
