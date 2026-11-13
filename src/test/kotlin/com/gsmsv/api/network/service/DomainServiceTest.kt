package com.gsmsv.api.network.service

import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.infra.network.FakeNetworkInfraClient
import com.gsmsv.api.infra.network.HttpsRoute
import com.gsmsv.api.network.config.HttpsDomainProperties
import com.gsmsv.api.network.domain.HttpsDomain
import com.gsmsv.api.network.domain.SyncStatus
import com.gsmsv.api.network.repository.HttpsDomainRepository
import com.gsmsv.api.network.repository.HttpsRouteLock
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.service.VmService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DomainServiceTest {
    private val domainRepository = mockk<HttpsDomainRepository>()
    private val routeLock = mockk<HttpsRouteLock>(relaxed = true)
    private val vmService = mockk<VmService>()
    private val infraClient = FakeNetworkInfraClient()
    private val service =
        DomainService(domainRepository, routeLock, vmService, infraClient, HttpsDomainProperties(baseDomain = "example.com"))

    @Test
    fun `목록은 VM의 도메인과 VM당 한도를 돌려준다`() {
        val vm = activeVm()
        every { domainRepository.findAllByVmIdOrderByCreatedAtAscIdAsc(vm.id) } returns
            listOf(HttpsDomain("domain-4k8m2q9t", vm.id, "my-app", 8080))

        val response = service.list(vm)

        assertEquals(2, response.limits.perVm)
        val item = response.items.single()
        assertEquals("domain-4k8m2q9t", item.id)
        assertEquals("https://my-app.example.com", item.url)
        assertEquals(8080, item.internalPort)
        assertEquals("pending", item.syncStatus)
    }

    @Test
    fun `연결하면 레코드를 저장하고 활성 도메인 전체를 한 번에 반영한다`() {
        val vm = activeVm()
        val other = HttpsDomain("domain-7m3k9p2x", OTHER_VM, "other", 3000)
        val saved = slot<HttpsDomain>()
        every { domainRepository.countByVmId(vm.id) } returns 0
        every { domainRepository.existsBySubdomain("my-app") } returns false
        every { domainRepository.saveAndFlush(capture(saved)) } answers { saved.captured }
        every { domainRepository.findAllByOrderByCreatedAtAscIdAsc() } answers { listOf(other, saved.captured) }
        every { vmService.internalIpsOf(setOf(OTHER_VM, vm.id)) } returns mapOf(OTHER_VM to "192.0.2.30", vm.id to VM_IP)

        val response = service.create(vm, "my-app", 8080)

        verify { routeLock.lock() }
        assertEquals("https://my-app.example.com", response.url)
        assertEquals("synced", response.syncStatus)
        assertTrue(response.id.matches(Regex("^domain-[0-9a-hjkmnp-tv-z]{8}$")))
        assertEquals(1, infraClient.routeSyncCalls.size)
        assertEquals(
            listOf(
                HttpsRoute("domain-7m3k9p2x", "other.example.com", "192.0.2.30:3000"),
                HttpsRoute(response.id, "my-app.example.com", "$VM_IP:8080"),
            ),
            infraClient.currentRoutes(),
        )
        assertEquals(SyncStatus.SYNCED, other.syncStatus)
    }

    @Test
    fun `지워졌거나 내부 IP가 없는 VM의 도메인은 라우트에서 뺀다`() {
        val vm = activeVm()
        val orphan = HttpsDomain("domain-7m3k9p2x", OTHER_VM, "orphan", 3000)
        val saved = slot<HttpsDomain>()
        every { domainRepository.countByVmId(vm.id) } returns 0
        every { domainRepository.existsBySubdomain("my-app") } returns false
        every { domainRepository.saveAndFlush(capture(saved)) } answers { saved.captured }
        every { domainRepository.findAllByOrderByCreatedAtAscIdAsc() } answers { listOf(orphan, saved.captured) }
        every { vmService.internalIpsOf(any()) } returns mapOf(vm.id to VM_IP)

        service.create(vm, "my-app", 8080)

        assertEquals(listOf("my-app.example.com"), infraClient.currentRoutes().map { it.host })
        assertEquals(SyncStatus.PENDING, orphan.syncStatus)
    }

    @Test
    fun `예약어는 400 VALIDATION_FAILED다`() {
        val error = assertThrows<BusinessException> { service.create(activeVm(), "admin", 8080) }

        assertEquals(ErrorCode.VALIDATION_FAILED, error.errorCode)
        assertTrue(infraClient.routeSyncCalls.isEmpty())
    }

    @Test
    fun `내부 IP가 없는 VM이면 409 VM_NO_INTERNAL_IP다`() {
        val error = assertThrows<BusinessException> { service.create(vm(), "my-app", 8080) }

        assertEquals(ErrorCode.VM_NO_INTERNAL_IP, error.errorCode)
    }

    @Test
    fun `VM당 2개를 넘으면 422 QUOTA_EXCEEDED이고 한도와 쓰는 개수를 알려 준다`() {
        val vm = activeVm()
        every { domainRepository.countByVmId(vm.id) } returns 2

        val error = assertThrows<BusinessException> { service.create(vm, "my-app", 8080) }

        assertEquals(ErrorCode.QUOTA_EXCEEDED, error.errorCode)
        assertEquals(mapOf("limit" to 2, "used" to 2), error.details)
        verify(exactly = 0) { domainRepository.saveAndFlush(any()) }
    }

    @Test
    fun `이미 쓰는 서브도메인이면 409 ALREADY_EXISTS다`() {
        val vm = activeVm()
        every { domainRepository.countByVmId(vm.id) } returns 0
        every { domainRepository.existsBySubdomain("my-app") } returns true

        val error = assertThrows<BusinessException> { service.create(vm, "my-app", 8080) }

        assertEquals(ErrorCode.ALREADY_EXISTS, error.errorCode)
    }

    @Test
    fun `저장하다 유니크 제약에 걸리면 409 ALREADY_EXISTS다`() {
        val vm = activeVm()
        every { domainRepository.countByVmId(vm.id) } returns 0
        every { domainRepository.existsBySubdomain("my-app") } returns false
        every { domainRepository.saveAndFlush(any()) } throws DataIntegrityViolationException("duplicate")

        val error = assertThrows<BusinessException> { service.create(vm, "my-app", 8080) }

        assertEquals(ErrorCode.ALREADY_EXISTS, error.errorCode)
        assertTrue(infraClient.routeSyncCalls.isEmpty())
    }

    @Test
    fun `게이트웨이 반영에 실패하면 예외를 그대로 던져 트랜잭션을 되돌린다`() {
        val vm = activeVm()
        val saved = slot<HttpsDomain>()
        every { domainRepository.countByVmId(vm.id) } returns 0
        every { domainRepository.existsBySubdomain("my-app") } returns false
        every { domainRepository.saveAndFlush(capture(saved)) } answers { saved.captured }
        every { domainRepository.findAllByOrderByCreatedAtAscIdAsc() } answers { listOf(saved.captured) }
        every { vmService.internalIpsOf(any()) } returns mapOf(vm.id to VM_IP)
        infraClient.failWith(BusinessException(ErrorCode.GATEWAY_SYNC_FAILED))

        val error = assertThrows<BusinessException> { service.create(vm, "my-app", 8080) }

        assertEquals(ErrorCode.GATEWAY_SYNC_FAILED, error.errorCode)
        assertEquals(SyncStatus.PENDING, saved.captured.syncStatus)
    }

    private fun activeVm(): Vm = vm().also { it.markActive(VM_IP) }

    private fun vm(): Vm =
        Vm.create(
            id = "vm-k3m9x2p7q4ta",
            name = "테스트 VM",
            purpose = null,
            owner = Owner.User("user-4p8w2k9m1xza"),
            image = "ubuntu-24.04",
            vcpu = 2,
            ramGb = 4,
            ssdGb = 20,
            initialUsername = "ubuntu",
            initialPasswordEncrypted = "encrypted",
            createdBy = "user-4p8w2k9m1xza",
            expiresAt = Instant.parse("2027-01-01T00:00:00Z"),
        )

    private companion object {
        const val VM_IP = "192.0.2.23"
        const val OTHER_VM = "vm-a2b3c4d5e6f7"
    }
}
