package com.gsmsv.api.auth.service

import com.gsmsv.api.auth.repository.RefreshTokenRepository
import com.gsmsv.api.auth.repository.StoredRefreshToken
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RefreshTokenServiceTest {
    private val repository = mockk<RefreshTokenRepository>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneOffset.UTC)
    private val service = RefreshTokenService(repository, clock)
    private val tokenHash = LoginStateService.sha256("old-refresh")

    @Test
    fun `유효한 refresh token을 한 번만 소비한다`() {
        every { repository.findByHashForUpdate(tokenHash) } returns
            StoredRefreshToken("user-0123456789ab", Instant.parse("2026-11-21T00:00:00Z"))
        every { repository.delete(tokenHash) } returns 1

        val userId = service.consume("old-refresh")

        assertEquals("user-0123456789ab", userId)
        verify(exactly = 1) { repository.delete(tokenHash) }
        verify(exactly = 0) { repository.save(any(), any(), any(), any()) }
    }

    @Test
    fun `재사용되거나 만료된 refresh token은 거절한다`() {
        every { repository.findByHashForUpdate(tokenHash) } returns null

        assertEquals(ErrorCode.UNAUTHENTICATED, assertFailsWith<BusinessException> { service.consume("old-refresh") }.errorCode)
        every { repository.findByHashForUpdate(tokenHash) } returns
            StoredRefreshToken("user-0123456789ab", clock.instant())
        assertEquals(ErrorCode.UNAUTHENTICATED, assertFailsWith<BusinessException> { service.consume("old-refresh") }.errorCode)
        verify(exactly = 0) { repository.save(any(), any(), any(), any()) }
    }

    @Test
    fun `logout은 토큰이 없어도 성공하고 토큰이 있으면 폐기한다`() {
        service.revoke(null)
        service.revoke("old-refresh")

        verify(exactly = 1) { repository.delete(tokenHash) }
    }
}
