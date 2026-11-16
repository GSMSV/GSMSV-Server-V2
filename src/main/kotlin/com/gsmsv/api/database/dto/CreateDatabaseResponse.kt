package com.gsmsv.api.database.dto

import com.gsmsv.api.operation.dto.OperationRefResponse

data class CreatedDatabaseResourceResponse(
    val grn: String,
    val status: String,
)

/** 202 응답. 접속 정보는 넣지 않는다. 만드는 일이 끝난 뒤 접속 정보 조회(7.5)로 받는다 */
data class CreateDatabaseResponse(
    val operation: OperationRefResponse,
    val resource: CreatedDatabaseResourceResponse,
)
