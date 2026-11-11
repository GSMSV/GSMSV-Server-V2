package com.gsmsv.api.infra.network

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FakeNetworkInfraClientTest {
    private val fake = FakeNetworkInfraClient()

    private fun rule(
        id: String,
        external: Int,
    ) = VmPortRule(id, external, 22, PortProtocol.TCP)

    @Test
    fun `받은 포트 집합을 기억하고 추가·회수 개수를 계산한다`() {
        val first = fake.syncVmPorts("vm-1", "192.0.2.10", listOf(rule("port-a", 1), rule("port-b", 2)))
        val second = fake.syncVmPorts("vm-1", "192.0.2.10", listOf(rule("port-b", 2), rule("port-c", 3)))

        assertEquals(PortSyncResult(true, added = 2, removed = 0), first)
        assertEquals(PortSyncResult(true, added = 1, removed = 1), second)
        assertEquals(listOf("port-b", "port-c"), fake.currentPorts("vm-1").map { it.id })
        assertEquals(2, fake.portSyncCalls.size)
    }

    @Test
    fun `빈 집합을 보내면 전부 회수한다`() {
        fake.syncVmPorts("vm-1", "192.0.2.10", listOf(rule("port-a", 1)))

        val result = fake.syncVmPorts("vm-1", "192.0.2.10", emptyList())

        assertEquals(1, result.removed)
        assertTrue(fake.currentPorts("vm-1").isEmpty())
    }

    @Test
    fun `마지막에 받은 HTTPS 라우트 목록이 현재 상태다`() {
        fake.syncHttpsRoutes(listOf(HttpsRoute("dom-a", "a.test", "u1")))
        fake.syncHttpsRoutes(listOf(HttpsRoute("dom-b", "b.test", "u2")))

        assertEquals(listOf("dom-b"), fake.currentRoutes().map { it.id })
    }

    @Test
    fun `지정한 오류로 실패시킬 수 있다`() {
        fake.failWith(BusinessException(ErrorCode.GATEWAY_SYNC_FAILED))

        val e = assertFailsWith<BusinessException> { fake.syncHttpsRoutes(emptyList()) }

        assertEquals(ErrorCode.GATEWAY_SYNC_FAILED, e.errorCode)
        assertTrue(fake.routeSyncCalls.isEmpty())
    }
}
