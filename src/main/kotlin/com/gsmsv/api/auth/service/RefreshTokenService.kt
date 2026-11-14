package com.gsmsv.api.auth.service

import com.gsmsv.api.auth.domain.RefreshToken
import com.gsmsv.api.auth.repository.RefreshTokenRepository
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64

@Service
class RefreshTokenService(
    private val repository: RefreshTokenRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val random = SecureRandom()

    @Transactional
    fun issue(userId: String): RefreshToken {
        val value = randomValue()
        val now = clock.instant()
        val expiresAt = now.plus(TTL)
        repository.save(LoginStateService.sha256(value), userId, now, expiresAt)
        return RefreshToken(userId, value, expiresAt)
    }

    @Transactional
    fun consume(value: String): String {
        val tokenHash = LoginStateService.sha256(value)
        val stored = repository.findByHashForUpdate(tokenHash) ?: throw BusinessException(ErrorCode.UNAUTHENTICATED)
        if (!stored.expiresAt.isAfter(clock.instant())) {
            throw BusinessException(ErrorCode.UNAUTHENTICATED)
        }
        if (repository.delete(tokenHash) != 1) throw BusinessException(ErrorCode.UNAUTHENTICATED)
        return stored.userId
    }

    @Transactional
    fun revoke(value: String?) {
        if (!value.isNullOrBlank()) repository.delete(LoginStateService.sha256(value))
    }

    private fun randomValue(): String {
        val bytes = ByteArray(VALUE_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        val TTL: Duration = Duration.ofDays(7)
        private const val VALUE_BYTES = 32
    }
}
