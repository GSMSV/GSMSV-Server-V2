package com.gsmsv.api.global.authorization

/**
 * 규칙표(21번)로 주체·소유자·동작을 판단한다.
 *
 * 판단 순서: DB의 소유자(개인/팀) → 그 팀에서 요청자의 역할 → 규칙표 (22번 규칙).
 */
class AuthorizationPolicy(
    private val teamMembershipReader: TeamMembershipReader,
) {
    /**
     * @param targetUserId 동작의 대상 사용자(예: DevOps 역할을 회수할 사람). 대상이 없는 동작은 null
     */
    fun decide(
        actor: Actor,
        owner: Owner,
        action: Action,
        targetUserId: String? = null,
    ): Decision = decideAll(actor, owner, listOf(action), targetUserId).getValue(action)

    /** 여러 동작을 한 번에 판단한다. 팀 역할은 한 번만 읽는다. 결과는 [actions] 순서를 따른다 */
    fun decideAll(
        actor: Actor,
        owner: Owner,
        actions: Collection<Action>,
        targetUserId: String? = null,
    ): Map<Action, Decision> =
        when (actor) {
            SystemActor -> {
                actions.associateWith { Decision.Allow }
            }

            is AdminActor -> {
                actions.associateWith { Decision.AllowAsAdmin }
            }

            is UserActor -> {
                val role = (owner as? Owner.Team)?.let { teamMembershipReader.findRole(it.id, actor.userId) }
                actions.associateWith { decideForUser(actor, owner, role, it, targetUserId) }
            }
        }

    /**
     * 특정 자원이 없는 플랫폼 전체 동작(관리자 인프라 현황 등)을 판단한다. 관리자 전용 동작만 받는다.
     * 소유자가 없으므로 관리자 규칙으로만 통과하고, 일반 사용자는 403이다
     */
    fun decidePlatform(
        actor: Actor,
        action: Action,
    ): Decision {
        require(action.grant == Grant.ADMIN_ONLY) { "$action 동작은 자원 없이 판단할 수 없습니다." }
        return when (actor) {
            SystemActor -> Decision.Allow
            is AdminActor -> Decision.AllowAsAdmin
            is UserActor -> Decision.Forbidden(action.grant, actorRole = null)
        }
    }

    /** 목록 조회에서 [actor]가 볼 수 있는 소유자. 관리자·SYSTEM은 전체를 보므로 null */
    fun visibleOwners(actor: Actor): Set<Owner>? =
        when (actor) {
            SystemActor, is AdminActor -> {
                null
            }

            is UserActor -> {
                val teams =
                    teamMembershipReader
                        .findMemberships(actor.userId)
                        .filterValues { RuleTable.allows(it, Action.VIEW) }
                        .keys
                        .map { Owner.Team(it) }
                setOf(Owner.User(actor.userId)) + teams
            }
        }

    /** @param role [owner]가 팀일 때 그 팀에서 요청자의 역할. 팀원이 아니거나 개인 자원이면 null */
    private fun decideForUser(
        actor: UserActor,
        owner: Owner,
        role: TeamRole?,
        action: Action,
        targetUserId: String?,
    ): Decision =
        when (owner) {
            // 자원 단위 공유 기능이 없으므로 남의 개인 자원은 존재 자체를 숨긴다.
            // 개인 자원 소유자의 권한 범위는 계약에서 "확인 필요"다 (22번 13장). 관리자 전용 동작만 뺀다
            is Owner.User -> {
                when {
                    owner.id != actor.userId -> Decision.NotFound
                    action.grant == Grant.ADMIN_ONLY -> Decision.Forbidden(action.grant, actorRole = null)
                    else -> Decision.Allow
                }
            }

            is Owner.Team -> {
                when {
                    role == null -> Decision.NotFound
                    RuleTable.allows(role, action, targetsSelf = targetUserId == actor.userId) -> Decision.Allow
                    else -> Decision.Forbidden(action.grant, role)
                }
            }
        }
}
