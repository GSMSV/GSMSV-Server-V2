package com.gsmsv.api.team.service

import com.gsmsv.api.global.authorization.TeamRole
import com.gsmsv.api.team.domain.TeamMember
import com.gsmsv.api.team.repository.TeamMemberRepository
import com.gsmsv.api.team.repository.TeamRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TeamServiceTest {
    private val teamRepository = mockk<TeamRepository>()
    private val teamMemberRepository = mockk<TeamMemberRepository>()
    private val teamService = TeamService(teamRepository, teamMemberRepository)

    @Test
    fun `팀 멤버십 역할을 매번 repository에서 읽는다`() {
        every { teamMemberRepository.findByTeamIdAndUserId(TEAM_ID, USER_ID) } returns
            TeamMember(MEMBER_ID, TEAM_ID, USER_ID, TeamRole.MEMBER)

        val firstRole = teamService.findRole(TEAM_ID, USER_ID)
        val secondRole = teamService.findRole(TEAM_ID, USER_ID)

        assertEquals(TeamRole.MEMBER, firstRole)
        assertEquals(TeamRole.MEMBER, secondRole)
        verify(exactly = 2) { teamMemberRepository.findByTeamIdAndUserId(TEAM_ID, USER_ID) }
    }

    @Test
    fun `사용자가 속한 팀별 역할을 조회한다`() {
        every { teamMemberRepository.findAllByUserId(USER_ID) } returns
            listOf(
                TeamMember(MEMBER_ID, TEAM_ID, USER_ID, TeamRole.LEADER),
                TeamMember(OTHER_MEMBER_ID, OTHER_TEAM_ID, USER_ID, TeamRole.DEVOPS),
            )

        assertEquals(
            mapOf(TEAM_ID to TeamRole.LEADER, OTHER_TEAM_ID to TeamRole.DEVOPS),
            teamService.findMemberships(USER_ID),
        )
    }

    @Test
    fun `팀원 전원의 사용자 id를 조회한다`() {
        every { teamMemberRepository.findAllByTeamId(TEAM_ID) } returns
            listOf(
                TeamMember(MEMBER_ID, TEAM_ID, USER_ID, TeamRole.LEADER),
                TeamMember(OTHER_MEMBER_ID, TEAM_ID, "user-b2c3d4e5f6g7", TeamRole.MEMBER),
            )

        assertEquals(listOf(USER_ID, "user-b2c3d4e5f6g7"), teamService.findMemberUserIds(TEAM_ID))
    }

    companion object {
        private const val USER_ID = "user-a1b2c3d4e5f6"
        private const val TEAM_ID = "team-c3d4e5f6g7h8"
        private const val OTHER_TEAM_ID = "team-d4e5f6g7h8j9"
        private const val MEMBER_ID = "mbr-2x7c5v9bq4ta"
        private const val OTHER_MEMBER_ID = "mbr-9k3m7q2p5wzh"
    }
}
