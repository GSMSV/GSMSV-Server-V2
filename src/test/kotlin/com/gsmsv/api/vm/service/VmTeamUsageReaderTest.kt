package com.gsmsv.api.vm.service

import com.gsmsv.api.team.service.TeamVmUsage
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class VmTeamUsageReaderTest {
    private val vmService = mockk<VmService>()

    @Test
    fun `팀 VM 사용량은 저장된 VM 사양 합계로 채운다`() {
        every { vmService.teamUsage("team-c3d4e5f6g7h8") } returns VmUsage(vcpu = 4, ramGib = 8, ssdGib = 40)

        val usage = VmTeamUsageReader(vmService).readUsage("team-c3d4e5f6g7h8")

        assertEquals(TeamVmUsage(vcpu = 4, ramGib = 8, ssdGib = 40), usage)
    }
}
