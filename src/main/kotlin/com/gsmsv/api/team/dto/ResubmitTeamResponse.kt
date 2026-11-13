package com.gsmsv.api.team.dto

import com.gsmsv.api.team.domain.Team

data class ResubmitTeamResponse(
    val id: String,
    val status: String,
) {
    companion object {
        fun from(team: Team) = ResubmitTeamResponse(team.id, team.status.name.lowercase())
    }
}
