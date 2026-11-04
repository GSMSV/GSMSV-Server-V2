package com.gsmsv.api.global.authorization

/**
 * 자원 응답의 `permissions`를 만든다 (22번 9장). 예: `{ "delete": false, "extend": true }`
 *
 * 로더와 같은 규칙표([AuthorizationPolicy])로 판단한다. 프론트는 버튼을 숨기는 데만 쓰고, 실제 차단은 로더가 한다.
 * 응답 값을 만들 뿐 접근을 허용하지 않으므로 감사 로그는 남기지 않는다.
 *
 * 팀 도메인에 [TeamMembershipReader] 구현이 생기면 Spring 빈으로 등록한다.
 */
class PermissionsResolver(
    private val policy: AuthorizationPolicy,
) {
    /** 로더로 불러온 서비스 자원에 대해 [actions]를 할 수 있는지. 키 순서는 [actions]를 따른다 */
    fun resolve(
        actor: Actor,
        resource: OwnedResource,
        actions: Collection<Action>,
    ): Map<String, Boolean> = toPermissions(policy.decideAll(actor, resource.owner, actions))

    /**
     * 팀에 대해 [actions]를 할 수 있는지. 승인되지 않은 팀이면 [Action.requiresApprovedTeam] 동작은 false다.
     *
     * 대상 사용자가 있어야 판단할 수 있는 동작([Grant.LEADER_OR_SELF_DEVOPS])은 본인 허용을 빼고 판단한다.
     * 팀원 목록처럼 대상마다 다른 값이 필요하면 그 응답에서 따로 판단한다.
     */
    fun resolve(
        actor: Actor,
        team: TeamResource,
        actions: Collection<Action>,
    ): Map<String, Boolean> {
        val decisions =
            policy.decideAll(actor, Owner.Team(team.id), actions).mapValues { (action, decision) ->
                if (decision.isAllowed && action.requiresApprovedTeam && !team.approved) Decision.TeamNotApproved else decision
            }
        return toPermissions(decisions)
    }

    private fun toPermissions(decisions: Map<Action, Decision>): Map<String, Boolean> =
        decisions.entries.associate { (action, decision) -> action.permissionKey to decision.isAllowed }
}
