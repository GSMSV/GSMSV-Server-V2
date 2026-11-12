package com.gsmsv.api.vm.service

import com.gsmsv.api.team.service.TeamVmUsage
import com.gsmsv.api.team.service.TeamVmUsageReader
import org.springframework.stereotype.Component

/** 팀 상세(3.3)의 VM 사용량을 저장된 VM 사양 합계로 채운다. */
@Component
class VmTeamUsageReader(
    private val vmService: VmService,
) : TeamVmUsageReader {
    override fun readUsage(teamId: String): TeamVmUsage {
        val usage = vmService.teamUsage(teamId)
        return TeamVmUsage(vcpu = usage.vcpu, ramGib = usage.ramGib, ssdGib = usage.ssdGib)
    }
}
