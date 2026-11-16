package com.gsmsv.api.global.response

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val code: String,
    val message: String,
    @JsonProperty("request_id")
    val requestId: String,
    val details: Map<String, Any?>? = null,
)

/** 검증 실패 응답의 `details.fields[]` 항목. 객체 단위 검증 실패는 [field]가 없다 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class FieldErrorDetail(
    val field: String?,
    val reason: String,
)
