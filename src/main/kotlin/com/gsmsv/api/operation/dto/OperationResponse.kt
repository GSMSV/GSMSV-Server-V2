package com.gsmsv.api.operation.dto

import com.gsmsv.api.operation.domain.Operation
import java.time.Instant

/** `GET /api/v2/operations/{operation_id}`의 응답이자 SSE `operation` 이벤트의 `data`. 값이 없는 칸도 null로 내보낸다 */
data class OperationResponse(
    val id: String,
    val type: String,
    val resource: OperationTargetResponse,
    val status: String,
    val stage: String,
    val queuePosition: Int,
    val error: OperationErrorResponse?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val finishedAt: Instant?,
) {
    companion object {
        fun from(operation: Operation) =
            OperationResponse(
                id = operation.id,
                type = operation.type.value,
                resource = OperationTargetResponse(operation.targetGrn),
                status = operation.status.value,
                stage = operation.stage.value,
                queuePosition = operation.queuePosition,
                error = operation.error?.let { OperationErrorResponse(it.code, it.message, it.retryable) },
                createdAt = operation.createdAt,
                updatedAt = operation.updatedAt,
                finishedAt = operation.finishedAt,
            )
    }
}

/** 작업 대상. 서비스 자원은 GRN, 하위 자원은 하위 자원 표기다 */
data class OperationTargetResponse(
    val grn: String,
)

data class OperationErrorResponse(
    val code: String,
    val message: String,
    val retryable: Boolean,
)
