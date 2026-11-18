package com.gsmsv.api.operation.dto

import com.gsmsv.api.operation.domain.Operation

/**
 * 오래 걸리는 요청의 `202` 응답 `{ operation, resource }`. [resource]는 각 도메인 명세가 정한 모양이다.
 *
 * 나머지 진행 상태는 `GET /api/v2/operations/{operation_id}`나 SSE로 받는다.
 */
data class OperationAcceptedResponse<R : Any>(
    val operation: OperationRefResponse,
    val resource: R,
) {
    companion object {
        fun <R : Any> of(
            operation: Operation,
            resource: R,
        ) = OperationAcceptedResponse(OperationRefResponse.from(operation), resource)
    }
}

/** Contracts-V2 `OperationRef`. `202` 응답의 `operation` 칸 */
data class OperationRefResponse(
    val id: String,
    val type: String,
    val status: String,
    val stage: String,
) {
    companion object {
        fun from(operation: Operation) =
            OperationRefResponse(
                id = operation.id,
                type = operation.type.value,
                status = operation.status.value,
                stage = operation.stage.value,
            )
    }
}
