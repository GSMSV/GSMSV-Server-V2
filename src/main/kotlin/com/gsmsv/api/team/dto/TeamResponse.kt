package com.gsmsv.api.team.dto

import com.gsmsv.api.team.domain.Team
import java.time.Instant

data class CreateTeamResponse(
    val id: String,
    val name: String,
    val status: String,
    val myRole: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(team: Team) = CreateTeamResponse(team.id, team.name, team.status.name.lowercase(), "leader", team.createdAt)
    }
}

data class TeamSummaryResponse(
    val id: String,
    val name: String,
    val status: String,
    val myRole: String,
    val memberCount: Int,
)

data class TeamDetailResponse(
    val id: String,
    val name: String,
    val status: String,
    val rejectionReason: String?,
    val myRole: String?,
    val members: List<TeamMemberResponse>,
    val quota: TeamQuotaResponse?,
    val permissions: Map<String, Boolean>,
)

data class TeamMemberResponse(
    val memberId: String,
    val name: String,
    val email: String,
    val role: String,
    val profileImageUrl: String? = null,
)

data class TeamQuotaResponse(
    val vm: VmQuotaResponse,
)

data class VmQuotaResponse(
    val vcpu: QuotaUsageResponse,
    val ramGib: QuotaUsageResponse,
    val ssdGib: QuotaUsageResponse,
)

data class QuotaUsageResponse(
    val used: Int,
    val limit: Int,
)
