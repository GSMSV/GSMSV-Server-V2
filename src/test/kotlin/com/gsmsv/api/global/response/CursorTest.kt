package com.gsmsv.api.global.response

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CursorTest {
    @Test
    fun `인코딩한 커서를 디코딩하면 원래 값이 나온다`() {
        val cursor = Cursor(Instant.parse("2026-11-04T01:02:03.123456Z"), "vm-k3m9x2p7q4ta")

        assertEquals(cursor, Cursor.decode(cursor.encode()))
    }

    @Test
    fun `인코딩한 커서는 URL에 그대로 넣을 수 있다`() {
        val encoded = Cursor(Instant.parse("2026-11-04T01:02:03Z"), "vm-k3m9x2p7q4ta").encode()

        assertFalse(encoded.any { it in "+/=" })
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "!!!", "bm90LWEtY3Vyc29y"])
    fun `형식이 틀린 커서는 INVALID_CURSOR로 거절한다`(value: String) {
        val exception = assertFailsWith<BusinessException> { Cursor.decode(value) }

        assertEquals(ErrorCode.INVALID_CURSOR, exception.errorCode)
    }

    @ParameterizedTest
    @ValueSource(strings = ["yesterday|vm-k3m9x2p7q4ta", "2026-11-04T01:02:03Z|", "2026-11-04T01:02:03Z|VM-X", "a|b|c"])
    fun `디코딩한 내용이 틀린 커서는 INVALID_CURSOR로 거절한다`(raw: String) {
        val value = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())

        val exception = assertFailsWith<BusinessException> { Cursor.decode(value) }

        assertEquals(ErrorCode.INVALID_CURSOR, exception.errorCode)
    }
}
