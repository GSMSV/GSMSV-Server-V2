package com.gsmsv.api.vm.domain

import com.gsmsv.api.global.authorization.Owner
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class VmTest {
    @Test
    fun `새 VM은 만드는 중 상태로 시작하고 내부 IP와 초기화 완료가 비어 있다`() {
        val vm = newVm()

        assertEquals(VmStatus.CREATING, vm.status)
        assertNull(vm.internalIp)
        assertEquals(false, vm.initialized)
    }

    @Test
    fun `소유자는 만들 때 정한 개인 또는 팀으로 돌려준다`() {
        assertEquals(Owner.User(USER_ID), newVm(Owner.User(USER_ID)).owner)
        assertEquals(Owner.Team(TEAM_ID), newVm(Owner.Team(TEAM_ID)).owner)
    }

    @Test
    fun `만들기를 마치면 내부 IP를 받고 사용 중이 된다`() {
        val vm = newVm()

        vm.markActive("10.0.0.5")

        assertEquals(VmStatus.ACTIVE, vm.status)
        assertEquals("10.0.0.5", vm.internalIp)
        assertFailsWith<IllegalStateException> { vm.markActive("10.0.0.6") }
    }

    @Test
    fun `만드는 중에 실패하면 실패 상태가 되고 삭제를 시작할 수 있다`() {
        val vm = newVm()

        vm.markFailed()
        vm.startDeleting()

        assertEquals(VmStatus.DELETING, vm.status)
    }

    @Test
    fun `정지된 VM을 연장하면 사용 중으로 돌아가고 만료는 앞으로만 늘린다`() {
        val vm = newVm()
        vm.markActive("10.0.0.5")
        vm.suspend()
        val later = EXPIRES_AT.plusSeconds(3600)

        vm.extend(later)

        assertEquals(VmStatus.ACTIVE, vm.status)
        assertEquals(later, vm.expiresAt)
        assertFailsWith<IllegalArgumentException> { vm.extend(EXPIRES_AT) }
    }

    @Test
    fun `삭제는 삭제 중을 거쳐야 하고 삭제 시각만 남긴다`() {
        val vm = newVm()
        vm.markActive("10.0.0.5")
        assertFailsWith<IllegalStateException> { vm.markDeleted() }

        vm.startDeleting()
        val at = Instant.parse("2026-12-01T00:00:00Z")
        vm.markDeleted(at)

        assertEquals(VmStatus.DELETED, vm.status)
        assertEquals(at, vm.deletedAt)
    }

    @Test
    fun `cloud-init 완료를 표시할 수 있다`() {
        val vm = newVm()

        vm.markInitialized()

        assertEquals(true, vm.initialized)
    }

    private fun newVm(owner: Owner = Owner.User(USER_ID)) =
        Vm.create(
            id = "vm-k3m9x2p7q4ta",
            name = "내 서버",
            purpose = null,
            owner = owner,
            image = "ubuntu-24.04",
            vcpu = 2,
            ramGb = 4,
            ssdGb = 20,
            initialUsername = "ubuntu",
            initialPasswordEncrypted = "encrypted",
            createdBy = USER_ID,
            expiresAt = EXPIRES_AT,
        )

    private companion object {
        const val USER_ID = "user-a1b2c3d4e5f6"
        const val TEAM_ID = "team-c3d4e5f6g7h8"
        val EXPIRES_AT: Instant = Instant.parse("2026-11-30T00:00:00Z")
    }
}
