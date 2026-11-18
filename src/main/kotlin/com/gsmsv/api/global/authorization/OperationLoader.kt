package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.IdParser

/**
 * 오래 걸리는 작업(operation) 로더 (operation id). operation에는 따로 권한이 없고 대상 자원의 권한으로 판단한다
 * (Contracts-V2 `openapi/operation.yaml`).
 *
 * 대상 자원을 다시 꺼내지 않고, 등록할 때 복사해 둔 대상의 DB 소유자([OperationRecord.targetOwner])로 판단한다.
 * 그래야 `vm.delete`처럼 대상이 지워진 작업도 보관 기간 동안 같은 규칙표로 볼 수 있다.
 * 팀 역할은 요청마다 다시 읽으므로, 팀에서 빠진 사람은 바로 볼 수 없게 된다.
 */
class OperationLoader(
    private val policy: AuthorizationPolicy,
    private val auditLogger: AuthorizationAuditLogger,
) {
    /**
     * @throws BusinessException id 형식 오류는 400, operation이 없거나 대상 자원을 볼 수 없으면 404, 권한이 없으면 403
     * @throws IllegalStateException 요청 안에서 엔드포인트의 [RequirePermission]과 다른 동작을 넘기면
     */
    fun <T : OperationRecord> load(
        actor: Actor,
        operationId: String,
        action: Action,
        reader: OperationReader<T>,
    ): T {
        DeclaredPermission.verify(action)
        IdParser.requireOperationId(operationId)
        val operation = reader.findById(operationId) ?: throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        val decision = policy.decide(actor, operation.targetOwner, action)
        if (decision == Decision.AllowAsAdmin) {
            auditLogger.record(actor, action, operationId, decision)
        }
        decision.throwIfDenied()
        return operation
    }
}
