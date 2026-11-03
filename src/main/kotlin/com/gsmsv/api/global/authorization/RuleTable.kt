package com.gsmsv.api.global.authorization

/** 규칙표(21번) 중 팀 역할에 대한 부분. 관리자·SYSTEM·개인 소유자 규칙은 [AuthorizationPolicy]가 맡는다. */
object RuleTable {
    fun allows(
        role: TeamRole,
        action: Action,
    ): Boolean =
        when (action.grant) {
            Grant.ALL_MEMBERS -> true
            Grant.LEADER_OR_DEVOPS -> role == TeamRole.LEADER || role == TeamRole.DEVOPS
            Grant.LEADER_ONLY -> role == TeamRole.LEADER
        }
}
