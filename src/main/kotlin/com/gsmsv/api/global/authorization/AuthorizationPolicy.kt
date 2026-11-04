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
    ): Decision =
        when (actor) {
            SystemActor -> Decision.Allow
            is AdminActor -> Decision.AllowAsAdmin
            is UserActor -> decideForUser(actor, owner, action, targetUserId)
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

    private fun decideForUser(
        actor: UserActor,
        owner: Owner,
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
                val role = teamMembershipReader.findRole(owner.id, actor.userId)
                when {
                    role == null -> Decision.NotFound
                    RuleTable.allows(role, action, targetsSelf = targetUserId == actor.userId) -> Decision.Allow
                    else -> Decision.Forbidden(action.grant, role)
                }
            }
        }
}
