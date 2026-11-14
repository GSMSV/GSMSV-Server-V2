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
import java.time.Duration
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
}
