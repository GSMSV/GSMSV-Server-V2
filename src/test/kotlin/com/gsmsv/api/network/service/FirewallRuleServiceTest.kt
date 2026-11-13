package com.gsmsv.api.network.service

import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.response.Cursor
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.infra.firewall.FakeFirewallInfraClient
import com.gsmsv.api.network.domain.FirewallAction
import com.gsmsv.api.network.domain.FirewallDirection
import com.gsmsv.api.network.domain.FirewallProtocol
import com.gsmsv.api.network.domain.FirewallRule
import com.gsmsv.api.network.domain.FirewallRuleSpec
import com.gsmsv.api.network.dto.CreateFirewallRuleRequest
import com.gsmsv.api.network.repository.FirewallSyncLock
import com.gsmsv.api.vm.domain.Vm
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirewallRuleServiceTest {
    private val store = mockk<FirewallRuleStore>(relaxed = true)
    private val syncLock = mockk<FirewallSyncLock>()
    private val infra = FakeFirewallInfraClient()
    private val service = FirewallRuleService(store, syncLock, infra)

    @BeforeEach
    fun setUp() {
        every { syncLock.withLock<Any?>(any(), any()) } answers { secondArg<() -> Any?>().invoke() }
    }

    @Test
    fun `추가하면 저장한 상태 전체를 VM 내부 IP와 함께 Go에 보내고 id를 돌려준다`() {
        val vm = activeVm()
        val existing = rule("fwrule-aaaaaaaa", Instant.parse("2026-11-01T00:00:00Z"))
        val added = rule("fwrule-bbbbbbbb", Instant.parse("2026-11-02T00:00:00Z"), dport = "8000:8100")
        every { store.add(vm.id, any(), FirewallRuleService.PER_VM_LIMIT) } returns
            AddedFirewallRule(added, firewallCreated = false, state = FirewallState(true, listOf(existing, added)))

        val response = service.create(vm, CreateFirewallRuleRequest(dport = "8000:8100", source = "203.0.113.0/24"))

        assertEquals("fwrule-bbbbbbbb", response.id)
        val call = infra.syncCalls.single()
        assertEquals(INTERNAL_IP, call.internalIp)
        assertTrue(call.enabled)
        assertEquals(listOf("fwrule-aaaaaaaa", "fwrule-bbbbbbbb"), call.rules.map { it.id })
        assertEquals("8000:8100", call.rules[1].dport)
        verify { syncLock.withLock<Any?>(vm.id, any()) }
        verify {
            store.add(
                vm.id,
                FirewallRuleSpec(FirewallAction.ACCEPT, FirewallDirection.IN, FirewallProtocol.TCP, "8000:8100", "203.0.113.0/24"),
                FirewallRuleService.PER_VM_LIMIT,
            )
        }
    }

    @Test
    fun `Go 반영에 실패하면 저장한 규칙을 되돌리고 그 오류를 돌려준다`() {
        val vm = activeVm()
        val added = AddedFirewallRule(rule("fwrule-bbbbbbbb"), firewallCreated = true, state = FirewallState(true, emptyList()))
        every { store.add(vm.id, any(), any()) } returns added
        infra.failWith(BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE))

        val exception = assertFailsWith<BusinessException> { service.create(vm, CreateFirewallRuleRequest()) }

        assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, exception.errorCode)
        verify { store.revertAdd(added) }
    }

    @Test
    fun `형식이 틀린 입력은 저장하지 않고 400이다`() {
        val exception =
            assertFailsWith<BusinessException> { service.create(activeVm(), CreateFirewallRuleRequest(dport = "70000")) }

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.errorCode)
        verify(exactly = 0) { store.add(any(), any(), any()) }
    }

    @Test
    fun `내부 IP가 없는 VM에는 규칙을 추가할 수 없다`() {
        val exception = assertFailsWith<BusinessException> { service.create(vm(), CreateFirewallRuleRequest()) }

        assertEquals(ErrorCode.CONFLICT, exception.errorCode)
        verify(exactly = 0) { store.add(any(), any(), any()) }
    }

    @Test
    fun `삭제는 그 규칙을 뺀 상태를 Go에 먼저 반영한 뒤 DB에서 지운다`() {
        val vm = activeVm()
        val target = rule("fwrule-bbbbbbbb")
        val remaining = rule("fwrule-aaaaaaaa")
        every { store.state(vm.id, target.id) } returns FirewallState(true, listOf(remaining))

        service.delete(vm, target)

        val call = infra.syncCalls.single()
        assertTrue(call.enabled)
        assertEquals(listOf("fwrule-aaaaaaaa"), call.rules.map { it.id })
        verifyOrder {
            store.state(vm.id, target.id)
            store.delete(target.id)
        }
    }

    @Test
    fun `삭제를 Go에 반영하지 못하면 DB에서 지우지 않는다`() {
        val vm = activeVm()
        val target = rule("fwrule-bbbbbbbb")
        every { store.state(vm.id, target.id) } returns FirewallState(true, emptyList())
        infra.failWith(BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE))

        assertFailsWith<BusinessException> { service.delete(vm, target) }

        verify(exactly = 0) { store.delete(any()) }
    }

    @Test
    fun `목록은 만든 순서로 커서를 넘기고 켜짐 여부와 VM당 한도를 함께 준다`() {
        val vm = activeVm()
        val first = rule("fwrule-aaaaaaaa", Instant.parse("2026-11-01T00:00:00Z"))
        val second = rule("fwrule-bbbbbbbb", Instant.parse("2026-11-02T00:00:00Z"))
        val third = rule("fwrule-cccccccc", Instant.parse("2026-11-03T00:00:00Z"))
        every { store.state(vm.id) } returns FirewallState(true, listOf(first, second, third))

        val page1 = service.list(vm, CursorPageRequest(limit = 2))
        val page2 = service.list(vm, CursorPageRequest(cursor = page1.nextCursor, limit = 2))

        assertTrue(page1.enabled)
        assertEquals(FirewallRuleService.PER_VM_LIMIT, page1.limits.perVm)
        assertEquals(listOf(first.id, second.id), page1.items.map { it.id })
        assertEquals(Cursor(second.createdAt, second.id).encode(), page1.nextCursor)
        assertEquals(listOf(third.id), page2.items.map { it.id })
        assertNull(page2.nextCursor)
    }

    @Test
    fun `규칙이 없는 VM의 목록은 비어 있고 방화벽은 꺼져 있다`() {
        val vm = activeVm()
        every { store.state(vm.id) } returns FirewallState(false, emptyList())

        val page = service.list(vm, CursorPageRequest())

        assertEquals(false, page.enabled)
        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }

    private fun vm(): Vm =
        Vm.create(
            id = "vm-k3m9x2p7q4ta",
            name = "테스트 VM",
            purpose = null,
            owner = Owner.User("user-a1b2c3d4e5f6"),
            image = "ubuntu-24.04",
            vcpu = 2,
            ramGb = 4,
            ssdGb = 20,
            initialUsername = "ubuntu",
            initialPasswordEncrypted = "encrypted",
            createdBy = "user-a1b2c3d4e5f6",
            expiresAt = Instant.parse("2027-01-01T00:00:00Z"),
        )

    private fun activeVm(): Vm = vm().also { it.markActive(INTERNAL_IP) }

    private fun rule(
        id: String,
        createdAt: Instant = Instant.parse("2026-11-01T00:00:00Z"),
        dport: String? = "80",
    ) = FirewallRule(id, "vm-k3m9x2p7q4ta", FirewallAction.ACCEPT, FirewallDirection.IN, FirewallProtocol.TCP, dport, null, createdAt)

    private companion object {
        // 문서용 예시 대역(RFC 5737)이다
        const val INTERNAL_IP = "192.0.2.10"
    }
}
