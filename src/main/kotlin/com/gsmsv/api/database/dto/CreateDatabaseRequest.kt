package com.gsmsv.api.database.dto

import com.gsmsv.api.operation.dto.OperationRefResponse
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/** `name`은 앞뒤 공백을 지운 뒤 1~30자다. 길이는 service가 검사한다 */
data class CreateDatabaseRequest(
    @field:NotBlank val name: String,
    @field:NotBlank
    @field:Pattern(regexp = "mysql|postgresql", message = "엔진은 mysql 또는 postgresql이어야 합니다.")
    val engine: String,
)

data class CreatedDatabaseResource(
    val grn: String,
    val status: String,
)

/** 202 응답. 접속 정보는 넣지 않는다. 만드는 일이 끝난 뒤 접속 정보 조회(7.5)로 받는다 */
data class CreateDatabaseResponse(
    val operation: OperationRefResponse,
    val resource: CreatedDatabaseResource,
)
