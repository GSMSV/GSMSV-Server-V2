package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.IdParser

/**
 * 팀 전용 로더 (팀 id). 서비스 자원과 같은 규칙표로 판단하고, 모든 판단을 감사 로그로 남긴다.
 *
 * 팀 VM·데이터베이스 생성처럼 팀 앞으로 자원을 만드는 동작도 이 로더로 팀을 불러 판단한다.
 *
 * 팀 도메인에 [TeamMembershipReader] 구현이 생기면 Spring 빈으로 등록한다.
 */
class TeamLoader(
    private val policy: AuthorizationPolicy,
    private val auditLogger: AuthorizationAuditLogger,
) {
    /**
     * @param targetUserId 동작의 대상 팀원. [Grant.LEADER_OR_SELF_DEVOPS] 동작(DevOps 역할 회수)에서는 반드시 넘긴다
     * @throws BusinessException id 형식 오류는 400, 팀이 없거나 팀원이 아니면 404, 역할이 모자라거나 승인되지 않은 팀이면 403
     * @throws IllegalStateException 요청 안에서 엔드포인트의 [RequirePermission]과 다른 동작을 넘기면
     */
    fun <T : TeamResource> load(
        actor: Actor,
        teamId: String,
        action: Action,
        reader: TeamReader<T>,
        targetUserId: String? = null,
    ): T {
        DeclaredPermission.verify(action)
        require(action.grant != Grant.LEADER_OR_SELF_DEVOPS || targetUserId != null) {
            "$action 동작은 대상 사용자 id가 있어야 판단할 수 있습니다."
        }
        IdParser.requireTeamId(teamId)
        targetUserId?.let { IdParser.requireUserId(it) }

        val team = reader.findById(teamId)
        if (team == null) {
            auditLogger.record(actor, action, teamId, Decision.NotFound)
            throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }
        var decision = policy.decide(actor, Owner.Team(team.id), action, targetUserId)
        // 승인 여부는 권한이 있는 사람에게만 알린다. 팀 밖 사람은 404, 역할이 모자란 사람은 403(권한)이 먼저다
        if (decision.isAllowed && action.requiresApprovedTeam && !team.approved) {
            decision = Decision.TeamNotApproved
        }
        auditLogger.record(actor, action, team.id, decision)
        decision.throwIfDenied()
        return team
    }
}
