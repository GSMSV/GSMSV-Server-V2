package com.gsmsv.api.team.service

import com.gsmsv.api.global.authorization.TeamRole
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.domain.TeamMember
import com.gsmsv.api.team.domain.TeamStatus
import com.gsmsv.api.team.repository.TeamMemberRepository
import com.gsmsv.api.team.repository.TeamRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun `이미 있는 팀원을 다시 추가하면 conflict로 거절한다`() {
        every { teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID) } returns true

        val exception = assertFailsWith<BusinessException> { teamService.addMember(TEAM_ID, USER_ID, TeamRole.MEMBER) }

        assertEquals(ErrorCode.ALREADY_MEMBER, exception.errorCode)
        verify(exactly = 0) { teamMemberRepository.saveAndFlush(any()) }
    }

    @Test
    fun `동시에 추가된 중복 멤버는 유니크 제약 오류를 conflict로 바꾼다`() {
        every { teamMemberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID) } returns false
        every { teamMemberRepository.saveAndFlush(any()) } throws org.springframework.dao.DataIntegrityViolationException("duplicate")

        val exception = assertFailsWith<BusinessException> { teamService.addMember(TEAM_ID, USER_ID, TeamRole.MEMBER) }

        assertEquals(ErrorCode.ALREADY_MEMBER, exception.errorCode)
    fun `거절된 팀은 이름을 다듬어 다시 신청한다`() {
        val team = Team(TEAM_ID, "기존 팀", TeamStatus.REJECTED, "사유")
        every { teamRepository.save(team) } returns team

        val resubmitted = teamService.resubmit(team, "  새 팀  ")

        assertEquals(team, resubmitted)
        assertEquals(TeamStatus.PENDING, team.status)
        assertEquals("새 팀", team.name)
        assertEquals("사유", team.rejectionReason)
        verify(exactly = 1) { teamRepository.save(team) }
    }

    @Test
    fun `거절 상태가 아니면 다시 신청할 수 없다`() {
        val team = Team(TEAM_ID, "기존 팀")

        val exception = assertFailsWith<BusinessException> { teamService.resubmit(team, null) }

        assertEquals(ErrorCode.CONFLICT, exception.errorCode)
    }

    @Test
    fun `이름을 수정할 때 공백 제거 뒤 길이를 검사한다`() {
        val team = Team(TEAM_ID, "기존 팀", TeamStatus.REJECTED, "사유")

        val exception = assertFailsWith<BusinessException> { teamService.resubmit(team, "   ") }

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.errorCode)
    }

    companion object {
        private const val USER_ID = "user-a1b2c3d4e5f6"
        private const val TEAM_ID = "team-c3d4e5f6g7h8"
        private const val OTHER_TEAM_ID = "team-d4e5f6g7h8j9"
        private const val MEMBER_ID = "mbr-2x7c5v9bq4ta"
        private const val OTHER_MEMBER_ID = "mbr-9k3m7q2p5wzh"
    }
}
