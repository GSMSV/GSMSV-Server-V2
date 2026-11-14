package com.gsmsv.api.infra.firewall

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeFirewallInfraClientTest {
    private val fake = FakeFirewallInfraClient()

    private fun rule(id: String) = VmFirewallRule(id, "accept", "in", "tcp", "80", null)

    @Test
    fun `VM별로 마지막에 받은 켜짐 여부와 규칙 목록을 기억한다`() {
        fake.syncVmFirewall("vm-1", "192.0.2.10", true, listOf(rule("fwrule-a"), rule("fwrule-b")))
        fake.syncVmFirewall("vm-1", "192.0.2.10", true, listOf(rule("fwrule-b")))

        assertTrue(fake.isEnabled("vm-1"))
        assertEquals(listOf("fwrule-b"), fake.currentRules("vm-1").map { it.id })
        assertFalse(fake.isEnabled("vm-2"))
        assertEquals(2, fake.syncCalls.size)
    }

    @Test
    fun `failWith를 지정하면 그 오류로 실패하고 상태를 바꾸지 않는다`() {
        fake.failWith(BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE))

        val exception = assertFailsWith<BusinessException> { fake.syncVmFirewall("vm-1", "192.0.2.10", true, emptyList()) }

        assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, exception.errorCode)
        assertTrue(fake.syncCalls.isEmpty())
    }

    @Test
    fun `실제 연결 전 구현은 언제나 503으로 실패한다`() {
        val exception =
            assertFailsWith<BusinessException> {
                NotConnectedFirewallInfraClient().syncVmFirewall("vm-1", "192.0.2.10", true, emptyList())
            }

        assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, exception.errorCode)
    }
}
