package com.gsmsv.api.auth.service

import com.gsmsv.api.auth.domain.RefreshToken
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.security.AccessToken
import com.gsmsv.api.global.security.AccessTokenProvider
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.domain.UserStatus
import com.gsmsv.api.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class IssuedTokens(
    val user: User,
    val accessToken: AccessToken,
    val refreshToken: RefreshToken,
    val redirect: String,
)

@Service
class TokenService(
    private val loginStateService: LoginStateService,
    private val userService: UserService,
    private val tokenProvider: AccessTokenProvider,
    private val refreshTokenService: RefreshTokenService,
) {
    @Transactional
    fun exchange(code: String): IssuedTokens {
        val loginCode = loginStateService.consumeCode(code)
        val user = userService.findById(loginCode.userId)
        if (user == null || user.status != UserStatus.ACTIVE || user.deletedAt != null) {
            throw BusinessException(ErrorCode.UNAUTHENTICATED)
        }
        val accessToken = tokenProvider.issue(user.id, user.role)
        val refreshToken = refreshTokenService.issue(user.id)
        return IssuedTokens(user, accessToken, refreshToken, loginCode.redirect)
    }
}
