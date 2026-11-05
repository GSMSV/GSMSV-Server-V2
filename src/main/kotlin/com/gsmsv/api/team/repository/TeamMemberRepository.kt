package com.gsmsv.api.team.repository

import com.gsmsv.api.team.domain.TeamMember
import org.springframework.data.jpa.repository.JpaRepository

interface TeamMemberRepository : JpaRepository<TeamMember, String> {
    fun findByTeamIdAndUserId(
        teamId: String,
        userId: String,
    ): TeamMember?

    fun findAllByTeamId(teamId: String): List<TeamMember>

    fun findAllByUserId(userId: String): List<TeamMember>
}
