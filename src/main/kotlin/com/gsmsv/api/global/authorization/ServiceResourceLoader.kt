package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.GrnParser

/**
 * 서비스 자원 로더 (GRN). 서비스 자원은 이 로더와 [listVisible]로만 꺼낸다 (22번 규칙).
 *
 * 팀 도메인에 [TeamMembershipReader] 구현이 생기면 Spring 빈으로 등록한다. 지금 등록하면 컨텍스트가 뜨지 않는다.
 */
class ServiceResourceLoader(
    private val policy: AuthorizationPolicy,
    private val auditLogger: AuthorizationAuditLogger,
) {
    /**
     * GRN으로 자원을 꺼내고 [action] 권한을 판단한다.
     *
     * @throws BusinessException GRN 형식·종류 오류는 400, 볼 수 없으면 404, 권한이 없으면 403
     */
    fun <T : OwnedResource> load(
        actor: Actor,
        grn: String,
        action: Action,
        reader: ServiceResourceReader<T>,
    ): T {
        val resourceId = GrnParser.parse(grn, reader.type)
        val resource = reader.findById(resourceId.value) ?: throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        // 요청 GRN의 소유자 칸은 믿지 않는다. DB 값으로 다시 만든 GRN과 통째로 같아야 한다
        if (GrnParser.format(resource.owner.id, resource.id) != grn) {
            throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }
        val decision = policy.decide(actor, resource.owner, action)
        if (decision == Decision.ALLOW_AS_ADMIN) {
            auditLogger.record(actor, action, grn, decision)
        }
        decision.throwIfDenied()
        return resource
    }

    /** [actor]가 볼 수 있는 자원 목록. 규칙표의 조회 권한으로 거른다. */
    fun <T : OwnedResource> listVisible(
        actor: Actor,
        reader: ServiceResourceReader<T>,
    ): List<T> {
        val owners = policy.visibleOwners(actor)
        if (owners == null) {
            if (actor is AdminActor) {
                auditLogger.record(actor, Action.VIEW, "list:${reader.type.prefix}", Decision.ALLOW_AS_ADMIN)
            }
            return reader.findAll()
        }
        // 구현체가 다른 소유자의 자원을 섞어 돌려줘도 새지 않도록 한 번 더 거른다
        return reader.findAllByOwners(owners).filter { it.owner in owners }
    }
}
