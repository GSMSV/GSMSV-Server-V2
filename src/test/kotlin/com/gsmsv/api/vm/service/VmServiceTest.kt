package com.gsmsv.api.vm.service

import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.domain.VmStatus
import com.gsmsv.api.vm.repository.VmRepository
import com.gsmsv.api.vm.repository.VmSpecSum
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class VmServiceTest {
    private val vmRepository = mockk<VmRepository>()
    private val vmService = VmService(vmRepository)

    @Test
    fun `개인 VM 사용량은 자원을 잡고 있는 상태의 사양 합계다`() {
        every { vmRepository.sumSpecByOwner(Vm.OwnerType.USER, USER_ID, any()) } returns VmSpecSum(3, 6, 50)

        val usage = vmService.personalUsage(USER_ID)

        assertEquals(VmUsage(vcpu = 3, ramGib = 6, ssdGib = 50), usage)
        verify {
            vmRepository.sumSpecByOwner(
                Vm.OwnerType.USER,
                USER_ID,
                setOf(VmStatus.CREATING, VmStatus.ACTIVE, VmStatus.SUSPENDED, VmStatus.DELETING),
            )
        }
    }

    @Test
    fun `VM이 없으면 사용량은 0이다`() {
        every { vmRepository.sumSpecByOwner(Vm.OwnerType.TEAM, TEAM_ID, any()) } returns VmSpecSum(0, 0, 0)

        assertEquals(VmUsage(0, 0, 0), vmService.teamUsage(TEAM_ID))
    }

    private companion object {
        const val USER_ID = "user-a1b2c3d4e5f6"
        const val TEAM_ID = "team-c3d4e5f6g7h8"
    }
}
