package com.gsmsv.api.auth.service

import com.gsmsv.api.auth.domain.LoginCode
import com.gsmsv.api.auth.domain.RefreshToken
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.security.AccessToken
import com.gsmsv.api.global.security.AccessTokenProvider
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.domain.UserStatus
import com.gsmsv.api.user.service.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TokenServiceTest {
    private val loginStateService = mockk<LoginStateService>()
    private val userService = mockk<UserService>()
    private val tokenProvider = mockk<AccessTokenProvider>()
    private val refreshTokenService = mockk<RefreshTokenService>()
    private val service = TokenService(loginStateService, userService, tokenProvider, refreshTokenService)
    private val user = User("user-0123456789ab", "dgs-1", "user@example.test", "사용자", Role.USER)

    @Test
    fun `refresh는 기본 롤백 규칙을 사용해 토큰 소모와 회전을 원자적으로 처리한다`() {
        val transaction = TokenService::class.java.getMethod("refresh", String::class.java).getAnnotation(Transactional::class.java)

        assertEquals(0, transaction.noRollbackFor.size)
    }

    @Test
    fun `유효한 코드면 access token과 refresh token을 발급하고 redirect를 돌려준다`() {
        val access = AccessToken("access", Duration.ofMinutes(15))
        val refresh = RefreshToken(user.id, "refresh", java.time.Instant.parse("2026-01-08T00:00:00Z"))
        every { loginStateService.consumeCode("tmp_code") } returns LoginCode(user.id, "/teams")
        every { userService.findById(user.id) } returns user
        every { tokenProvider.issue(user.id, user.role) } returns access
        every { refreshTokenService.issue(user.id) } returns refresh

        val result = service.exchange("tmp_code")

        assertEquals(user, result.user)
        assertEquals(access, result.accessToken)
        assertEquals(refresh, result.refreshToken)
        assertEquals("/teams", result.redirect)
    }

    @Test
    fun `비활성 사용자는 거부하고 임시 코드는 소모한다`() {
        val inactiveUser = User(user.id, "dgs-1", "user@example.test", "사용자", status = UserStatus.INACTIVE)
        every { loginStateService.consumeCode("tmp_code") } returns LoginCode(user.id, "/")
        every { userService.findById(user.id) } returns inactiveUser

        assertEquals(ErrorCode.UNAUTHENTICATED, assertFailsWith<BusinessException> { service.exchange("tmp_code") }.errorCode)
        verify(exactly = 1) { loginStateService.consumeCode("tmp_code") }
        verify(exactly = 0) { tokenProvider.issue(any(), any()) }
    }

    @Test
    fun `활성 사용자의 refresh token을 회전하고 새 access token을 발급한다`() {
        val rotated = RefreshToken(user.id, "rotated", Instant.parse("2026-01-15T00:00:00Z"))
        val access = AccessToken("new-access", Duration.ofMinutes(15))
        every { refreshTokenService.consume("old-refresh") } returns user.id
        every { refreshTokenService.issue(user.id) } returns rotated
        every { userService.findById(user.id) } returns user
        every { tokenProvider.issue(user.id, user.role) } returns access

        val result = service.refresh("old-refresh")

        assertEquals(user, result.user)
        assertEquals(rotated, result.refreshToken)
        assertEquals(access, result.accessToken)
        verify(exactly = 1) { refreshTokenService.consume("old-refresh") }
        verify(exactly = 1) { refreshTokenService.issue(user.id) }
    }

    @Test
    fun `탈퇴한 계정은 refresh를 거절한다`() {
        val withdrawn = User(user.id, "dgs-1", "user@example.test", "사용자", deletedAt = Instant.parse("2026-01-01T00:00:00Z"))
        every { refreshTokenService.consume("old-refresh") } returns user.id
        every { userService.findById(user.id) } returns withdrawn

        assertEquals(ErrorCode.GONE, assertFailsWith<BusinessException> { service.refresh("old-refresh") }.errorCode)
        verify(exactly = 0) { tokenProvider.issue(any(), any()) }
        verify(exactly = 0) { refreshTokenService.issue(any()) }
    }
}
