package com.gsmsv.api.global.authorization

/** 규칙표(21번) 중 팀 역할에 대한 부분. 관리자·SYSTEM·개인 소유자 규칙은 [AuthorizationPolicy]가 맡는다. */
object RuleTable {
    /**
     * @param targetsSelf 동작의 대상 사용자가 요청자 본인인지. [Grant.LEADER_OR_SELF_DEVOPS]에서만 쓴다.
     *   모르면 false다. 그러면 본인 허용이 빠질 뿐 더 넓게 허용되지는 않는다
     */
    fun allows(
        role: TeamRole,
        action: Action,
        targetsSelf: Boolean = false,
    ): Boolean =
        when (action.grant) {
            Grant.ALL_MEMBERS -> true
            Grant.LEADER_OR_DEVOPS -> role == TeamRole.LEADER || role == TeamRole.DEVOPS
            Grant.LEADER_ONLY -> role == TeamRole.LEADER
            Grant.LEADER_OR_SELF_DEVOPS -> role == TeamRole.LEADER || (targetsSelf && role == TeamRole.DEVOPS)
            Grant.ADMIN_ONLY -> false
        }
}
