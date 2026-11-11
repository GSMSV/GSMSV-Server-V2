package com.gsmsv.api.global.response

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets

/**
 * 파괴적 동작(삭제 등)의 확인 헤더 검사 도우미. controller에서 헤더 값을 그대로 넘긴다.
 *
 * 헤더는 퍼센트 인코딩된 값이다. 디코딩 뒤 대상 값과 정확히 같아야 한다(공백 제거·대소문자 무시 없음).
 * 헤더가 없거나 인코딩이 깨졌으면 400 `VALIDATION_FAILED`, 값이 다르면 400 `CONFIRMATION_MISMATCH`.
 */
object ConfirmationHeaders {
    const val NAME_HEADER = "X-Confirm-Name"
    const val EMAIL_HEADER = "X-Confirm-Email"

    fun requireName(
        header: String?,
        expected: String,
    ) = require(NAME_HEADER, header, expected)

    fun requireEmail(
        header: String?,
        expected: String,
    ) = require(EMAIL_HEADER, header, expected)

    private fun require(
        headerName: String,
        header: String?,
        expected: String,
    ) {
        if (header == null) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, mapOf("header" to headerName))
        }
        // '+'는 공백으로 바꾸지 않는다. 퍼센트 인코딩만 푼다
        val decoded =
            try {
                UriUtils.decode(header, StandardCharsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                throw BusinessException(ErrorCode.VALIDATION_FAILED, mapOf("header" to headerName))
            }
        // 응답에 기대값을 싣지 않는다. 요청자가 모르는 자원 정보가 새면 안 된다
        if (decoded != expected) {
            throw BusinessException(ErrorCode.CONFIRMATION_MISMATCH, mapOf("header" to headerName))
        }
    }
}
