package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode

/**
 * 팀 전용 로더 (팀 id). 서비스 자원과 같은 규칙표로 판단하고, 모든 판단을 감사 로그로 남긴다.
 *
 * 팀 도메인에 [TeamMembershipReader] 구현이 생기면 Spring 빈으로 등록한다.
 */
class TeamLoader(
    private val policy: AuthorizationPolicy,
    private val auditLogger: AuthorizationAuditLogger,
) {
    /**
     * @throws BusinessException 팀이 없거나 팀원이 아니면 404, 역할이 모자라면 403
     */
    fun <T : TeamResource> load(
        actor: Actor,
        teamId: String,
        action: Action,
        reader: TeamReader<T>,
    ): T {
        val team = reader.findById(teamId)
        if (team == null) {
            auditLogger.record(actor, action, teamId, Decision.NOT_FOUND)
            throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }
        val decision = policy.decide(actor, Owner.Team(team.id), action)
        auditLogger.record(actor, action, team.id, decision)
        decision.throwIfDenied()
        return team
    }
}
