package com.gsmsv.api.team.repository

import com.gsmsv.api.team.domain.TeamMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TeamMemberRepository : JpaRepository<TeamMember, String> {
    fun findByTeamIdAndUserId(
        teamId: String,
        userId: String,
    ): TeamMember?

    fun findAllByTeamId(teamId: String): List<TeamMember>

    fun findAllByUserId(userId: String): List<TeamMember>

    fun findAllByTeamIdOrderByIdAsc(teamId: String): List<TeamMember>

    @Query(
        """
        select teamMember.teamId as teamId, count(teamMember) as memberCount
        from TeamMember teamMember
        where teamMember.teamId in :teamIds
        group by teamMember.teamId
        """,
    )
    fun countByTeamIds(
        @Param("teamIds") teamIds: Collection<String>,
    ): List<TeamMemberCount>
}

interface TeamMemberCount {
    fun getTeamId(): String

    fun getMemberCount(): Long
}
