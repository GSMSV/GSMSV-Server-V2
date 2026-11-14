package com.gsmsv.api.network.service

import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.infra.network.FakeNetworkInfraClient
import com.gsmsv.api.infra.network.PortProtocol
import com.gsmsv.api.infra.network.VmPortRule
import com.gsmsv.api.network.config.PortForwardProperties
import com.gsmsv.api.network.domain.PortForward
import com.gsmsv.api.network.domain.PortForwardKind
import com.gsmsv.api.network.domain.PortForwardProtocol
import com.gsmsv.api.network.dto.CreatePortForwardRequest
import com.gsmsv.api.network.repository.PortForwardLock
import com.gsmsv.api.network.repository.PortForwardRepository
import com.gsmsv.api.vm.domain.Vm
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortForwardServiceTest {
    private val repository = mockk<PortForwardRepository>()
    private val lock = mockk<PortForwardLock>(relaxed = true)
    private val infra = FakeNetworkInfraClient()
    private val properties = PortForwardProperties(publicHost = "vm.example.com", customPortMin = 40000, customPortMax = 40009)
    private val service = PortForwardService(repository, lock, infra, properties, Random(42))

    @Test
    fun `목록은 그 VM의 포트를 소문자 종류·프로토콜로 주고 접속 주소와 커스텀 한도를 함께 준다`() {
        val vm = activeVm()
        every { repository.findAllByVmIdOrderByCreatedAtAscIdAsc(vm.id) } returns
            listOf(
                PortForward.createDefault("port-5q8w3n7z", vm.id, PortForwardKind.SSH, 20022, 22),
                PortForward.createCustom("port-2x7c5v9b", vm.id, 40001, 8080, PortForwardProtocol.UDP, "203.0.113.0/24", "개발용"),
            )

        val response = service.list(vm)

        assertEquals("vm.example.com", response.publicHost)
        assertEquals(30, response.limits.custom)
        assertEquals(listOf("ssh", "custom"), response.items.map { it.kind })
        assertEquals("udp", response.items[1].protocol)
        assertEquals("203.0.113.0/24", response.items[1].allowedSource)
        assertEquals(null, response.items[0].allowedSource)
    }

    @Test
    fun `커스텀 포트를 범위 안의 빈 외부 포트로 저장하고 VM의 포트 집합 전체를 Go에 보낸다`() {
        val vm = activeVm()
        val existing = PortForward.createDefault("port-5q8w3n7z", vm.id, PortForwardKind.SSH, 20022, 22)
        val saved = slot<PortForward>()
        every { repository.countByVmIdAndKind(vm.id, PortForwardKind.CUSTOM) } returns 0
        every { repository.existsByExternalPort(any()) } returns false
        every { repository.saveAndFlush(capture(saved)) } answers { saved.captured }
        every { repository.findAllByVmIdOrderByCreatedAtAscIdAsc(vm.id) } answers { listOf(existing, saved.captured) }

        val response = service.create(vm, request())

        assertTrue(response.externalPort in 40000..40009)
        assertEquals(saved.captured.id, response.id)
        assertTrue(response.id.matches(Regex("^port-[0-9a-hjkmnp-tv-z]{8}$")))
        assertEquals(8080, response.internalPort)
        assertEquals("tcp", response.protocol)
        assertEquals("synced", response.syncStatus)
        assertEquals(PortForwardKind.CUSTOM, saved.captured.kind)
        assertEquals(
            listOf(
                VmPortRule("port-5q8w3n7z", 20022, 22, PortProtocol.TCP, null),
                VmPortRule(response.id, response.externalPort, 8080, PortProtocol.TCP, "203.0.113.0/24"),
            ),
            infra.currentPorts(vm.id),
        )
        assertEquals(INTERNAL_IP, infra.portSyncCalls.single().internalIp)
        verifyOrder {
            lock.lock(vm.id)
            repository.countByVmIdAndKind(vm.id, PortForwardKind.CUSTOM)
            repository.saveAndFlush(any())
        }
    }

    @Test
    fun `이미 쓰는 외부 포트는 건너뛰고 빈 번호를 고른다`() {
        val vm = activeVm()
        val saved = slot<PortForward>()
        every { repository.countByVmIdAndKind(vm.id, PortForwardKind.CUSTOM) } returns 0
        every { repository.existsByExternalPort(any()) } returnsMany listOf(true, true, false)
        every { repository.saveAndFlush(capture(saved)) } answers { saved.captured }
        every { repository.findAllByVmIdOrderByCreatedAtAscIdAsc(vm.id) } answers { listOf(saved.captured) }

        service.create(vm, request())

        verify(exactly = 3) { repository.existsByExternalPort(any()) }
    }

    @Test
    fun `커스텀 포트가 한도만큼 있으면 422 QUOTA_EXCEEDED이고 한도와 쓰는 개수를 알려 준다`() {
        val vm = activeVm()
        every { repository.countByVmIdAndKind(vm.id, PortForwardKind.CUSTOM) } returns 30

        val exception = assertThrows<BusinessException> { service.create(vm, request()) }

        assertEquals(ErrorCode.QUOTA_EXCEEDED, exception.errorCode)
        assertEquals(mapOf("limit" to 30, "used" to 30), exception.details)
        verify(exactly = 0) { repository.saveAndFlush(any()) }
        assertTrue(infra.portSyncCalls.isEmpty())
    }

    @Test
    fun `100번 골라도 빈 외부 포트가 없으면 503 NO_FREE_PORT다`() {
        val vm = activeVm()
        every { repository.countByVmIdAndKind(vm.id, PortForwardKind.CUSTOM) } returns 0
        every { repository.existsByExternalPort(any()) } returns true

        val exception = assertThrows<BusinessException> { service.create(vm, request()) }

        assertEquals(ErrorCode.NO_FREE_PORT, exception.errorCode)
        verify(exactly = 100) { repository.existsByExternalPort(any()) }
        verify(exactly = 0) { repository.saveAndFlush(any()) }
    }

    @Test
    fun `다른 요청이 같은 외부 포트를 먼저 저장했으면 409 CONFLICT이고 Go에 보내지 않는다`() {
        val vm = activeVm()
        every { repository.countByVmIdAndKind(vm.id, PortForwardKind.CUSTOM) } returns 0
        every { repository.existsByExternalPort(any()) } returns false
        every { repository.saveAndFlush(any()) } throws DataIntegrityViolationException("duplicate")

        val exception = assertThrows<BusinessException> { service.create(vm, request()) }

        assertEquals(ErrorCode.CONFLICT, exception.errorCode)
        assertTrue(infra.portSyncCalls.isEmpty())
    }

    @Test
    fun `Go 반영이 실패하면 예외를 그대로 던져 트랜잭션을 되돌린다`() {
        val vm = activeVm()
        val saved = slot<PortForward>()
        every { repository.countByVmIdAndKind(vm.id, PortForwardKind.CUSTOM) } returns 0
        every { repository.existsByExternalPort(any()) } returns false
        every { repository.saveAndFlush(capture(saved)) } answers { saved.captured }
        every { repository.findAllByVmIdOrderByCreatedAtAscIdAsc(vm.id) } answers { listOf(saved.captured) }
        infra.failWith(BusinessException(ErrorCode.GATEWAY_SYNC_FAILED))

        val exception = assertThrows<BusinessException> { service.create(vm, request()) }

        assertEquals(ErrorCode.GATEWAY_SYNC_FAILED, exception.errorCode)
    }

    @Test
    fun `내부 IP가 없는 VM에는 추가할 수 없어 409 VM_NO_INTERNAL_IP다`() {
        val vm = newVm()

        val exception = assertThrows<BusinessException> { service.create(vm, request()) }

        assertEquals(ErrorCode.VM_NO_INTERNAL_IP, exception.errorCode)
        verify(exactly = 0) { repository.saveAndFlush(any()) }
    }

    @Test
    fun `기본 포트는 종류마다 하나씩 TCP·모두 허용으로 저장한다`() {
        val vm = activeVm()
        every { repository.saveAllAndFlush(any<List<PortForward>>()) } answers { firstArg() }

        val saved =
            service.saveDefaults(
                vm,
                listOf(
                    DefaultPortForward(PortForwardKind.SSH, 20022, 22),
                    DefaultPortForward(PortForwardKind.HTTP, 20080, 80),
                ),
            )

        assertEquals(listOf(PortForwardKind.SSH, PortForwardKind.HTTP), saved.map { it.kind })
        assertTrue(saved.all { it.protocol == PortForwardProtocol.TCP && it.allowedSource == null && it.vmId == vm.id })
        assertThrows<IllegalArgumentException> {
            service.saveDefaults(vm, listOf(DefaultPortForward(PortForwardKind.SSH, 1, 22), DefaultPortForward(PortForwardKind.SSH, 2, 22)))
        }
    }

    private fun request() =
        CreatePortForwardRequest(internalPort = 8080, protocol = "tcp", allowedSource = "203.0.113.0/24", description = "개발용")

    private fun activeVm(): Vm = newVm().also { it.markActive(INTERNAL_IP) }

    private fun newVm(): Vm =
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

    private companion object {
        const val INTERNAL_IP = "192.0.2.23"
    }
}
