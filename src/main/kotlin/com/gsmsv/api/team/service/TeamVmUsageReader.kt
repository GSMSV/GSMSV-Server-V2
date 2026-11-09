package com.gsmsv.api.team.service

import org.springframework.stereotype.Component

interface TeamVmUsageReader {
    fun readUsage(teamId: String): TeamVmUsage
}

data class TeamVmUsage(
    val vcpu: Int,
    val ramGib: Int,
    val ssdGib: Int,
)

@Component
class EmptyTeamVmUsageReader : TeamVmUsageReader {
    override fun readUsage(teamId: String) = TeamVmUsage(vcpu = 0, ramGib = 0, ssdGib = 0)
}
