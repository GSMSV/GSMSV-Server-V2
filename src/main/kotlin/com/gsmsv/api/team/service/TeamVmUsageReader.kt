package com.gsmsv.api.team.service

interface TeamVmUsageReader {
    fun readUsage(teamId: String): TeamVmUsage
}

data class TeamVmUsage(
    val vcpu: Int,
    val ramGib: Int,
    val ssdGib: Int,
)
