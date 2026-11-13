package com.gsmsv.api.global.response

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class ConfirmationHeadersTest {
    @Test
    fun `퍼센트 인코딩한 이름이 같으면 통과한다`() {
        ConfirmationHeaders.requireName("%EC%A3%BC%EB%AC%B8%20DB", "주문 DB")
    }

    @Test
    fun `인코딩하지 않은 ASCII 이름도 통과한다`() {
        ConfirmationHeaders.requireName("orders", "orders")
    }

    @Test
    fun `플러스 기호를 공백으로 바꾸지 않는다`() {
        ConfirmationHeaders.requireName("a+b", "a+b")
        assertCode(ErrorCode.CONFIRMATION_MISMATCH) { ConfirmationHeaders.requireName("a+b", "a b") }
    }

    @Test
    fun `이름이 다르면 400 CONFIRMATION_MISMATCH를 던진다`() {
        assertCode(ErrorCode.CONFIRMATION_MISMATCH) { ConfirmationHeaders.requireName("other", "orders") }
    }

    @Test
    fun `대소문자와 앞뒤 공백까지 정확히 비교한다`() {
        assertCode(ErrorCode.CONFIRMATION_MISMATCH) { ConfirmationHeaders.requireName("Orders", "orders") }
        assertCode(ErrorCode.CONFIRMATION_MISMATCH) { ConfirmationHeaders.requireName("orders%20", "orders") }
    }

    @Test
    fun `헤더가 없으면 400 VALIDATION_FAILED를 던진다`() {
        assertCode(ErrorCode.VALIDATION_FAILED) { ConfirmationHeaders.requireName(null, "orders") }
    }

    @Test
    fun `퍼센트 인코딩이 깨졌으면 400 VALIDATION_FAILED를 던진다`() {
        assertCode(ErrorCode.VALIDATION_FAILED) { ConfirmationHeaders.requireName("%E0%A4%A", "orders") }
    }

    @Test
    fun `이메일도 같은 방식으로 비교한다`() {
        ConfirmationHeaders.requireEmail("user%40gsm.hs.kr", "user@gsm.hs.kr")
        assertCode(ErrorCode.CONFIRMATION_MISMATCH) { ConfirmationHeaders.requireEmail("x%40gsm.hs.kr", "user@gsm.hs.kr") }
        assertCode(ErrorCode.VALIDATION_FAILED) { ConfirmationHeaders.requireEmail(null, "user@gsm.hs.kr") }
    }

    @Test
    fun `응답 details에 기대값을 싣지 않는다`() {
        val e = assertThrows<BusinessException> { ConfirmationHeaders.requireName("other", "secret-name") }
        assertEquals(mapOf<String, Any?>("header" to "X-Confirm-Name"), e.details)
    }

    private fun assertCode(
        code: ErrorCode,
        block: () -> Unit,
    ) = assertEquals(code, assertThrows<BusinessException> { block() }.errorCode)
}
