package com.gsmsv.api.team.service

import com.gsmsv.api.global.authorization.TeamRole
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.team.config.SchoolEmailProperties
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.domain.TeamInvitation
import com.gsmsv.api.team.repository.TeamInvitationRepository
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.domain.UserStatus
import com.gsmsv.api.user.service.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TeamInvitationAcceptTest {
    private val repository = mockk<TeamInvitationRepository>()
    private val teamService = mockk<TeamService>()
    private val userService = mockk<UserService>()
    private val service =
        TeamInvitationService(
            repository,
            mockk(),
            teamService,
            userService,
            mockk(),
            SchoolEmailProperties("example.test"),
        )
    private val team = Team("team-c3d4e5f6g7h8", "학교팀")
    private val user = User("user-b2c3d4e5f6g7", "school-1", "student@example.test", "학생", Role.USER, UserStatus.ACTIVE)
    private val invitation =
        TeamInvitation(
            "inv-c3d4e5f6g7h8",
            team.id,
            user.email,
            "0".repeat(64),
            Instant.now(),
            Instant.now().plusSeconds(3600),
        )

    @Test
    fun `유효한 초대는 팀원으로 추가하고 수락 응답을 돌려준다`() {
        prepareAccept()

        val response = service.accept(user.id, "secret-token")

        assertEquals(team.id, response.team.id)
        assertEquals(team.name, response.team.name)
        assertEquals("member", response.myRole)
        assertNotNull(invitation.acceptedAt)
        verify { teamService.addMember(team.id, user.id, TeamRole.MEMBER) }
    }

    @Test
    fun `이메일이 다르면 초대를 소비하지 않는다`() {
        prepareAccept(user.copyWithEmail("other@example.test"))

        val exception = assertFailsWith<BusinessException> { service.accept(user.id, "secret-token") }

        assertEquals(ErrorCode.INVITATION_EMAIL_MISMATCH, exception.errorCode)
        assertNull(invitation.acceptedAt)
        verify(exactly = 0) { teamService.addMember(any(), any(), any()) }
    }

    @Test
    fun `이미 팀원이면 초대를 소비하지 않는다`() {
        prepareAccept(existingRole = TeamRole.MEMBER)

        val exception = assertFailsWith<BusinessException> { service.accept(user.id, "secret-token") }

        assertEquals(ErrorCode.ALREADY_MEMBER, exception.errorCode)
        assertNull(invitation.acceptedAt)
        verify(exactly = 0) { teamService.addMember(any(), any(), any()) }
    }

    @Test
    fun `동시 중복 멤버 등록 충돌이면 초대를 수락 처리하지 않는다`() {
        prepareAccept()
        every { teamService.addMember(team.id, user.id, TeamRole.MEMBER) } throws BusinessException(ErrorCode.ALREADY_MEMBER)

        val exception = assertFailsWith<BusinessException> { service.accept(user.id, "secret-token") }

        assertEquals(ErrorCode.ALREADY_MEMBER, exception.errorCode)
        assertNull(invitation.acceptedAt)
    }

    @Test
    fun `만료된 초대는 gone으로 거절한다`() {
        val expired = TeamInvitation(invitation.id, team.id, user.email, "0".repeat(64), Instant.now(), Instant.now().minusSeconds(1))
        every { repository.findByTokenHashForUpdate(any()) } returns expired

        val exception = assertFailsWith<BusinessException> { service.accept(user.id, "secret-token") }

        assertEquals(ErrorCode.GONE, exception.errorCode)
        verify(exactly = 0) { userService.findById(any()) }
    }

    @Test
    fun `취소되었거나 이미 사용된 초대는 gone으로 거절한다`() {
        val canceled =
            TeamInvitation(invitation.id, team.id, user.email, "0".repeat(64), Instant.now(), Instant.now().plusSeconds(60), Instant.now())
        every { repository.findByTokenHashForUpdate(any()) } returns canceled

        val canceledException = assertFailsWith<BusinessException> { service.accept(user.id, "secret-token") }

        assertEquals(ErrorCode.GONE, canceledException.errorCode)
        val accepted =
            TeamInvitation(
                invitation.id,
                team.id,
                user.email,
                "0".repeat(64),
                Instant.now(),
                Instant.now().plusSeconds(60),
                initialAcceptedAt = Instant.now(),
            )
        every { repository.findByTokenHashForUpdate(any()) } returns accepted

        val acceptedException = assertFailsWith<BusinessException> { service.accept(user.id, "secret-token") }

        assertEquals(ErrorCode.GONE, acceptedException.errorCode)
    }

    @Test
    fun `없는 토큰은 gone으로 거절한다`() {
        every { repository.findByTokenHashForUpdate(any()) } returns null

        val exception = assertFailsWith<BusinessException> { service.accept(user.id, "secret-token") }

        assertEquals(ErrorCode.GONE, exception.errorCode)
    }

    private fun prepareAccept(
        requestedUser: User = user,
        existingRole: TeamRole? = null,
    ) {
        every { repository.findByTokenHashForUpdate(any()) } returns invitation
        every { userService.findById(user.id) } returns requestedUser
        every { teamService.findRole(team.id, user.id) } returns existingRole
        every { teamService.findById(team.id) } returns team
        every { teamService.addMember(team.id, user.id, TeamRole.MEMBER) } returns Unit
    }

    private fun User.copyWithEmail(email: String) = User(id, dataGsmId, email, name, role, status)
}
