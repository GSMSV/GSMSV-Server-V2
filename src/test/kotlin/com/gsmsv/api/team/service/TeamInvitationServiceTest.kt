package com.gsmsv.api.team.service

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.TeamRole
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.team.config.SchoolEmailProperties
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.domain.TeamStatus
import com.gsmsv.api.team.repository.TeamInvitationRepository
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.domain.UserStatus
import com.gsmsv.api.user.service.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class TeamInvitationServiceTest {
    private val repository = mockk<TeamInvitationRepository>(relaxed = true)
    private val loader = mockk<com.gsmsv.api.global.authorization.TeamLoader>()
    private val teamService = mockk<TeamService>()
    private val users = mockk<UserService>()
    private val mail = mockk<InvitationMailSender>(relaxed = true)
    private val service = TeamInvitationService(repository, loader, teamService, users, mail, SchoolEmailProperties("example.test"))
    private val actor = UserActor("user-a1b2c3d4e5f6")
    private val team = Team("team-c3d4e5f6g7h8", "학교팀", TeamStatus.PENDING)

    @Test
    fun `가입 여부와 관계없이 토큰 해시만 저장하고 정규화한 주소로 메일을 보낸다`() {
        every { loader.load(actor, team.id, Action.INVITE_MEMBER, teamService) } returns team
        every { users.findByEmail("student@example.test") } returns null
        every { repository.findByTeamIdAndEmailAndAcceptedAtIsNullAndCanceledAtIsNull(team.id, "student@example.test") } returns null
        val saved = slot<com.gsmsv.api.team.domain.TeamInvitation>()
        every { repository.saveAndFlush(capture(saved)) } answers { firstArg() }

        service.invite(actor, team.id, "Student@example.test ")

        val token = slot<String>()
        verify { mail.send("student@example.test", capture(token)) }
        assertEquals(
            32,
            java.util.Base64
                .getUrlDecoder()
                .decode(token.captured)
                .size,
        )
        assertNotEquals(token.captured, saved.captured.tokenHash)
        assertEquals(IdGenerator.invitationId().length, saved.captured.id.length)
        assertEquals("student@example.test", saved.captured.email)
    }

    @Test
    fun `거절된 팀은 초대할 수 없다`() {
        every { loader.load(actor, team.id, Action.INVITE_MEMBER, teamService) } returns Team(team.id, team.name, TeamStatus.REJECTED)

        val exception = assertFailsWith<BusinessException> { service.invite(actor, team.id, "student@example.test") }

        assertEquals(ErrorCode.TEAM_NOT_APPROVED, exception.errorCode)
        verify(exactly = 0) { mail.send(any(), any()) }
    }

    @Test
    fun `학교 도메인이 아닌 주소는 초대하지 않는다`() {
        every { loader.load(actor, team.id, Action.INVITE_MEMBER, teamService) } returns team

        val exception = assertFailsWith<BusinessException> { service.invite(actor, team.id, "student@other.test") }

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.errorCode)
    }

    @Test
    fun `대소문자가 다른 기존 회원 이메일도 이미 팀원으로 처리한다`() {
        every { loader.load(actor, team.id, Action.INVITE_MEMBER, teamService) } returns team
        every { users.findByEmail("student@example.test") } returns
            User("user-b2c3d4e5f6g7", "school-1", "Student@Example.Test", "학생", Role.USER, UserStatus.ACTIVE)
        every { teamService.findRole(team.id, "user-b2c3d4e5f6g7") } returns TeamRole.MEMBER

        val exception = assertFailsWith<BusinessException> { service.invite(actor, team.id, "student@example.test") }

        assertEquals(ErrorCode.ALREADY_MEMBER, exception.errorCode)
        verify(exactly = 0) { repository.saveAndFlush(any()) }
        verify(exactly = 0) { mail.send(any(), any()) }
    }

    @Test
    fun `잘못된 초대 id는 팀 조회 전에 거절한다`() {
        val exception = assertFailsWith<BusinessException> { service.cancel(actor, team.id, "invalid") }

        assertEquals(ErrorCode.INVALID_RESOURCE_ID, exception.errorCode)
        verify(exactly = 0) { loader.load(actor, team.id, Action.INVITE_MEMBER, teamService) }
    }
}
