package com.gsmsv.api.operation.domain

/**
 * 실패한 작업의 사용자용 원인. Infra의 원래 code와 `details`(VM 번호 등 구현 값)는 담지 않는다 (api-rules 2.2).
 *
 * [message]는 화면에 그대로 보여도 되는 한국어 문장이다.
 */
data class OperationError(
    val code: String,
    val message: String,
    val retryable: Boolean,
)
