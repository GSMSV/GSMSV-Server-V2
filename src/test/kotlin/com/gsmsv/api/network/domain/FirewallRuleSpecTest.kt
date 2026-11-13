package com.gsmsv.api.network.domain

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.response.FieldErrorDetail
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FirewallRuleSpecTest {
    @Test
    fun `빠진 값은 accept·in·tcp와 모든 포트·출발지로 채운다`() {
        val spec = FirewallRuleSpec.of(null, null, null, null, null)

        assertEquals(FirewallAction.ACCEPT, spec.action)
        assertEquals(FirewallDirection.IN, spec.direction)
        assertEquals(FirewallProtocol.TCP, spec.protocol)
        assertNull(spec.dport)
        assertNull(spec.source)
    }

    @Test
    fun `소문자 enum 값과 포트 범위·CIDR을 받는다`() {
        val spec = FirewallRuleSpec.of("drop", "out", "udp", "8000:8100", "203.0.113.0/24")

        assertEquals(
            FirewallRuleSpec(FirewallAction.DROP, FirewallDirection.OUT, FirewallProtocol.UDP, "8000:8100", "203.0.113.0/24"),
            spec,
        )
    }

    @Test
    fun `포트 앞의 0은 떼어 저장한다`() {
        assertEquals("80", FirewallRuleSpec.of(null, null, null, "080", null).dport)
    }

    @ParameterizedTest
    @ValueSource(strings = ["203.0.113.5", "0.0.0.0/0", "2001:db8::1", "2001:db8::/32"])
    fun `IP와 CIDR 출발지를 받는다`(source: String) {
        assertEquals(source, FirewallRuleSpec.of(null, null, null, null, source).source)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "65536", "100:80", "80:", "abc", "80-90", "1:2:3"])
    fun `범위를 벗어나거나 형식이 틀린 포트는 400이다`(dport: String) {
        assertFieldError("dport") { FirewallRuleSpec.of(null, null, null, dport, null) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["256.0.0.1", "203.0.113.0/33", "01.2.3.4", "example.com", "203.0.113.0/24/1", "2001:db8::/129", ":::"])
    fun `IP나 CIDR이 아닌 출발지는 400이다`(source: String) {
        assertFieldError("source") { FirewallRuleSpec.of(null, null, null, null, source) }
    }

    @Test
    fun `대문자나 모르는 enum 값은 400이다`() {
        val exception = assertFailsWith<BusinessException> { FirewallRuleSpec.of("ACCEPT", "both", "sctp", null, null) }

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.errorCode)
        assertEquals(listOf("action", "direction", "protocol"), fields(exception))
    }

    @Test
    fun `icmp 규칙에 포트를 지정하면 400이다`() {
        assertFieldError("dport") { FirewallRuleSpec.of(null, null, "icmp", "80", null) }
    }

    private fun assertFieldError(
        field: String,
        block: () -> Unit,
    ) {
        val exception = assertFailsWith<BusinessException> { block() }
        assertEquals(ErrorCode.VALIDATION_FAILED, exception.errorCode)
        assertEquals(listOf(field), fields(exception))
    }

    private fun fields(exception: BusinessException): List<String?> =
        (exception.details!!["fields"] as List<*>).filterIsInstance<FieldErrorDetail>().map { it.field }
}
