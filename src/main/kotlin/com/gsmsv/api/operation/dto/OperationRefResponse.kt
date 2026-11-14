package com.gsmsv.api.operation.dto

import com.gsmsv.api.operation.domain.Operation

/** 202 응답이 싣는 operation의 최소 표시 필드 (`openapi/database.yaml`의 `OperationRef`) */
data class OperationRefResponse(
    val id: String,
    val type: String,
    val status: String,
    val stage: String?,
) {
    companion object {
        fun from(operation: Operation) = OperationRefResponse(operation.id, operation.type, operation.status.key, operation.stage)
    }
}
