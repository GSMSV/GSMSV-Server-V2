package com.gsmsv.api.app.service

import com.gsmsv.api.app.domain.App
import com.gsmsv.api.app.domain.AppOwnerType
import com.gsmsv.api.app.repository.AppRepository
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.PermissionsResolver
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.domain.TeamStatus
import com.gsmsv.api.user.dto.UserSummary
import com.gsmsv.api.user.service.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppCommandServiceTest {
    private val repository = mockk<AppRepository>()
    private val userService = mockk<UserService>()
    private val permissionsResolver = mockk<PermissionsResolver>()
    private val service = AppCommandService(repository, userService, permissionsResolver)
    private val user = AuthenticatedUser(USER_ID, Role.USER)

    init {
        every { repository.countByOwnerTypeAndOwnerId(any(), any()) } returns 0
        every { repository.save(any()) } answers { firstArg() }
        every { userService.findSummariesByIds(listOf(USER_ID)) } returns
            mapOf(USER_ID to UserSummary(USER_ID, "만든 사람", "creator@example.com"))
        every { permissionsResolver.resolve(any(), any<App>(), any()) } returns
            mapOf("view" to true, "update" to true, "extend" to true, "delete" to true)
    }

    @Test
    fun `개인 앱은 본인 소유이고 기본 환경과 상세 응답을 만든다`() {
        val before = Instant.now()
        val response = service.createPersonalApp(user, "  같은 이름  ")
        val saved = slot<App>()
        verify { repository.save(capture(saved)) }

        assertEquals(Owner.User(USER_ID), saved.captured.owner)
        assertEquals(USER_ID, saved.captured.createdByUserId)
        assertEquals("같은 이름", response.name)
        assertEquals("grn:$USER_ID:${response.id}", response.grn)
        assertEquals(USER_ID, response.owner.id)
        assertEquals("만든 사람", response.owner.name)
        assertEquals("만든 사람", response.createdBy)
        assertEquals("production", response.envs.single().name)
        assertEquals(
            saved.captured.environments
                .single()
                .id,
            response.envs.single().id,
        )
        assertTrue(response.services.isEmpty() && response.domains.isEmpty() && response.deployments.isEmpty())
        assertTrue(response.expiresAt in before.plus(Duration.ofDays(14))..Instant.now().plus(Duration.ofDays(14)))
    }

    @Test
    fun `팀 앱은 팀 소유이고 개인 앱 개수와 별도로 센다`() {
        val team = Team(TEAM_ID, "우리 팀", TeamStatus.APPROVED)
        val response = service.createTeamApp(user, team, "앱")
        val saved = slot<App>()
        verify { repository.countByOwnerTypeAndOwnerId(AppOwnerType.TEAM, TEAM_ID) }
        verify { repository.save(capture(saved)) }

        assertEquals(Owner.Team(TEAM_ID), saved.captured.owner)
        assertEquals("우리 팀", response.owner.name)
        assertEquals("production", response.envs.single().name)
        assertTrue(response.expiresAt in Instant.now().plus(Duration.ofDays(31)).minusSeconds(5)..Instant.now().plus(Duration.ofDays(31)))
    }

    @Test
    fun `관리자 개인 앱의 첫 만료는 31일 뒤다`() {
        val response = service.createPersonalApp(AuthenticatedUser(USER_ID, Role.ADMIN), "앱")
        assertTrue(response.expiresAt in Instant.now().plus(Duration.ofDays(31)).minusSeconds(5)..Instant.now().plus(Duration.ofDays(31)))
    }

    @Test
    fun `개인 두 개와 팀 네 개 한도에서 추가 생성을 막는다`() {
        every { repository.countByOwnerTypeAndOwnerId(AppOwnerType.USER, USER_ID) } returns 2
        assertEquals(ErrorCode.QUOTA_EXCEEDED, assertThrows<BusinessException> { service.createPersonalApp(user, "앱") }.errorCode)
        every { repository.countByOwnerTypeAndOwnerId(AppOwnerType.TEAM, TEAM_ID) } returns 4
        assertEquals(
            ErrorCode.QUOTA_EXCEEDED,
            assertThrows<BusinessException> { service.createTeamApp(user, Team(TEAM_ID, "팀", TeamStatus.APPROVED), "앱") }.errorCode,
        )
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `앱 이름은 공백 제거 후 한 글자와 서른 글자를 허용한다`() {
        assertEquals("가", service.createPersonalApp(user, "  가  ").name)
        assertEquals("가".repeat(30), service.createPersonalApp(user, "  ${"가".repeat(30)}  ").name)
    }

    @Test
    fun `공백뿐인 이름과 서른한 글자는 검증 오류다`() {
        for (name in listOf("   ", "가".repeat(31))) {
            assertEquals(
                ErrorCode.VALIDATION_FAILED,
                assertThrows<BusinessException> { service.createPersonalApp(user, name) }.errorCode,
            )
        }
        verify(exactly = 0) { repository.save(any()) }
    }

    companion object {
        private const val USER_ID = "user-a1b2c3d4e5f6"
        private const val TEAM_ID = "team-4p8w2k9m1xza"
    }
}
