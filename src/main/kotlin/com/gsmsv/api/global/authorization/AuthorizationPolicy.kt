package com.gsmsv.api.global.authorization

/**
 * 규칙표(21번)로 주체·소유자·동작을 판단한다.
 *
 * 판단 순서: DB의 소유자(개인/팀) → 그 팀에서 요청자의 역할 → 규칙표 (22번 규칙).
 */
class AuthorizationPolicy(
    private val teamMembershipReader: TeamMembershipReader,
) {
    fun decide(
        actor: Actor,
        owner: Owner,
        action: Action,
    ): Decision =
        when (actor) {
            SystemActor -> Decision.ALLOW
            is AdminActor -> Decision.ALLOW_AS_ADMIN
            is UserActor -> decideForUser(actor, owner, action)
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
    ): Decision =
        when (owner) {
            // 자원 단위 공유 기능이 없으므로 남의 개인 자원은 존재 자체를 숨긴다
            is Owner.User -> {
                if (owner.id == actor.userId) Decision.ALLOW else Decision.NOT_FOUND
            }

            is Owner.Team -> {
                val role = teamMembershipReader.findRole(owner.id, actor.userId)
                when {
                    role == null -> Decision.NOT_FOUND
                    RuleTable.allows(role, action) -> Decision.ALLOW
                    else -> Decision.FORBIDDEN
                }
            }
        }
}
