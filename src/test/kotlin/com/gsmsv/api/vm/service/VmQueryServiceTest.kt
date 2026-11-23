package com.gsmsv.api.vm.service

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.PermissionsResolver
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.infra.vm.VmLiveState
import com.gsmsv.api.infra.vm.VmPower
import com.gsmsv.api.network.dto.DomainLimits
import com.gsmsv.api.network.dto.DomainListResponse
import com.gsmsv.api.network.dto.DomainResponse
import com.gsmsv.api.network.service.DomainService
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.service.TeamService
import com.gsmsv.api.user.dto.UserSummary
import com.gsmsv.api.user.service.UserService
import com.gsmsv.api.vm.domain.Vm
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VmQueryServiceTest {
    private val loader = mockk<ServiceResourceLoader>()
    private val reader = mockk<VmResourceReader>()
    private val permissions = mockk<PermissionsResolver>()
    private val liveStatus = mockk<VmLiveStatusReader>()
    private val users = mockk<UserService>()
    private val teams = mockk<TeamService>()
    private val domains = mockk<DomainService>()
    private val service = VmQueryService(loader, reader, permissions, liveStatus, users, teams, domains)
    private val actor = UserActor(USER_ID)

    @Test
    fun `목록은 생성 최신 순으로 커서 페이지를 나눈다`() {
        val old = vm("vm-aaaaaaaaaaaa", 1)
        val middle = vm("vm-bbbbbbbbbbbb", 2)
        val newest = vm("vm-cccccccccccc", 3)
        every { loader.listVisible(actor, reader, null) } returns listOf(old, newest, middle)
        stubNamesAndPermissions()
        every { liveStatus.read(any()) } returns RUNNING

        val first = service.list(actor, null, CursorPageRequest(limit = 2))
        assertEquals(listOf(newest.id, middle.id), first.items.map { it.id })
        assertNotNull(first.nextCursor)

        val second = service.list(actor, null, CursorPageRequest(cursor = first.nextCursor, limit = 2))
        assertEquals(listOf(old.id), second.items.map { it.id })
        assertNull(second.nextCursor)
    }

    @Test
    fun `목록 항목은 명세 칸을 채우고 목록 버튼 동작만 판단한다`() {
        val vm = vm("vm-k3m9x2p7q4ta", 1, Owner.Team(TEAM_ID), purpose = "주문 API").also { it.markActive("192.0.2.10") }
        every { loader.listVisible(actor, reader, Owner.Team(TEAM_ID)) } returns listOf(vm)
        every { users.findSummariesByIds(any()) } returns emptyMap()
        every { teams.findById(TEAM_ID) } returns Team(TEAM_ID, "주문팀")
        val actions = slot<Collection<Action>>()
        every { permissions.resolve(actor, vm, capture(actions)) } returns mapOf("delete" to false)
        every { liveStatus.read(vm) } returns RUNNING

        val item = service.list(actor, Owner.Team(TEAM_ID), CursorPageRequest()).items.single()

        assertEquals("grn:$TEAM_ID:vm-k3m9x2p7q4ta", item.grn)
        assertEquals("주문팀", item.owner.name)
        assertEquals("active", item.status)
        assertEquals("running", item.powerState)
        assertEquals(12.5, item.usage?.cpuPercent)
        assertEquals("192.0.2.10", item.internalIp)
        assertEquals("주문 API", item.purpose)
        assertEquals(2, item.spec.vcpu)
        assertEquals(listOf(Action.DELETE, Action.POWER, Action.EXTEND), actions.captured.toList())
    }

    @Test
    fun `실시간 값을 못 받은 VM만 unknown이다`() {
        val ok = vm("vm-aaaaaaaaaaaa", 1)
        val broken = vm("vm-bbbbbbbbbbbb", 2)
        every { loader.listVisible(actor, reader, null) } returns listOf(ok, broken)
        stubNamesAndPermissions()
        every { liveStatus.read(ok) } returns RUNNING
        every { liveStatus.read(broken) } returns VmLiveStatus.UNKNOWN

        val items = service.list(actor, null, CursorPageRequest()).items.associateBy { it.id }

        assertEquals("running", items.getValue(ok.id).powerState)
        assertEquals("unknown", items.getValue(broken.id).powerState)
        assertNull(items.getValue(broken.id).usage)
    }

    @Test
    fun `상세는 하위 자원과 전체 permissions를 한 응답에 담는다`() {
        val vm = vm("vm-k3m9x2p7q4ta", 1).also { it.markActive("192.0.2.10") }
        every { users.findSummariesByIds(any()) } returns mapOf(USER_ID to UserSummary(USER_ID, "홍길동", "a@example.com"))
        val actions = slot<Collection<Action>>()
        every { permissions.resolve(actor, vm, capture(actions)) } returns mapOf("view" to true)
        every { liveStatus.read(vm) } returns VmLiveStatus.UNKNOWN
        val domain = DomainResponse("domain-4k8m2q9t", "order-api", "https://order-api.example.com", 8080, "synced")
        every { domains.list(vm) } returns DomainListResponse(listOf(domain), DomainLimits(perVm = 2))

        val detail = service.get(actor, vm)

        assertEquals("grn:$USER_ID:vm-k3m9x2p7q4ta", detail.grn)
        assertEquals("홍길동", detail.owner.name)
        assertEquals("홍길동", detail.createdBy)
        assertEquals("unknown", detail.powerState)
        assertNull(detail.usage)
        assertEquals(listOf(domain), detail.domains)
        assertTrue(detail.snapshots.isEmpty() && detail.portForwards.isEmpty() && detail.firewallRules.isEmpty())
        assertNull(detail.ssh)
        assertEquals(11, actions.captured.size)
        assertEquals(Action.VIEW, actions.captured.first())
    }

    private fun stubNamesAndPermissions() {
        every { users.findSummariesByIds(any()) } returns mapOf(USER_ID to UserSummary(USER_ID, "홍길동", "a@example.com"))
        every { permissions.resolve(actor, any<Vm>(), any()) } returns mapOf("delete" to true)
    }

    private fun vm(
        id: String,
        day: Long,
        owner: Owner = Owner.User(USER_ID),
        purpose: String? = null,
    ) = Vm(
        id = id,
        initialName = "VM $day",
        purpose = purpose,
        ownerType = if (owner is Owner.User) Vm.OwnerType.USER else Vm.OwnerType.TEAM,
        ownerId = owner.id,
        image = "ubuntu-24.04",
        vcpu = 2,
        ramGb = 4,
        ssdGb = 40,
        initialUsername = "ubuntu",
        initialPasswordEncrypted = "encrypted",
        createdBy = USER_ID,
        initialExpiresAt = Instant.parse("2027-01-01T00:00:00Z"),
        initialCreatedAt = Instant.parse("2026-11-01T00:00:00Z").plusSeconds(day * 86_400),
    )

    private companion object {
        const val USER_ID = "user-a1b2c3d4e5f6"
        const val TEAM_ID = "team-4p8w2k9m1xza"
        val RUNNING =
            VmLiveStatus(VmPowerState.RUNNING, VmLiveState(VmPower.RUNNING, cpuPercent = 12.5, memoryPercent = 40.0, uptimeSec = 60))
    }
}
