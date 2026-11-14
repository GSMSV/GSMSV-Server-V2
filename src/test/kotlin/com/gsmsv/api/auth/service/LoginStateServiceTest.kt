package com.gsmsv.api.auth.service

import com.gsmsv.api.auth.domain.LoginCode
import com.gsmsv.api.auth.domain.LoginState
import com.gsmsv.api.auth.repository.LoginRecordRepository
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LoginStateServiceTest {
    private val repository = mockk<LoginRecordRepository>(relaxed = true)
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val service = LoginStateService(repository, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `state를 발급하면 해시와 정리한 redirect와 5분 만료를 저장한다`() {
        val hash = slot<String>()
        val redirect = slot<String>()
        val expiresAt = slot<Instant>()
        every { repository.saveState(capture(hash), capture(redirect), now, capture(expiresAt)) } returns Unit

        val state = service.issueState("/teams")

        assertEquals(LoginStateService.sha256(state), hash.captured)
        assertFalse(hash.captured.contains(state), "원본 값은 저장하지 않는다")
        assertEquals("/teams", redirect.captured)
        assertEquals(now.plusSeconds(300), expiresAt.captured)
        assertTrue(state.length >= 43, "32바이트 이상의 랜덤 값")
    }

    @Test
    fun `발급할 때마다 다른 값이 나온다`() {
        assertNotEquals(service.issueState(null), service.issueState(null))
    }

    @Test
    fun `임시 코드는 tmp_ 접두사와 사용자 id와 60초 만료로 저장한다`() {
        val expiresAt = slot<Instant>()
        every { repository.saveCode(any(), "user-0123456789ab", "/x", now, capture(expiresAt)) } returns Unit

        val code = service.issueCode("user-0123456789ab", "/x")

        assertTrue(code.startsWith("tmp_"))
        assertEquals(now.plusSeconds(60), expiresAt.captured)
        verify { repository.saveCode(LoginStateService.sha256(code), "user-0123456789ab", "/x", now, any()) }
    }

    @Test
    fun `state를 소모하면 저장한 redirect를 돌려준다`() {
        every { repository.consumeState(LoginStateService.sha256("s1"), now) } returns LoginState("/teams")

        assertEquals("/teams", service.consumeState("s1").redirect)
    }

    @Test
    fun `없거나 이미 쓴 state는 OAUTH_STATE_INVALID다`() {
        every { repository.consumeState(any(), any()) } returns null

        assertEquals(ErrorCode.OAUTH_STATE_INVALID, assertFailsWith<BusinessException> { service.consumeState("x") }.errorCode)
    }

    @Test
    fun `임시 코드를 소모하면 사용자와 redirect를 돌려준다`() {
        every { repository.consumeCode(LoginStateService.sha256("tmp_c"), now) } returns LoginCode("user-0123456789ab", "/a")

        val code = service.consumeCode("tmp_c")

        assertEquals("user-0123456789ab", code.userId)
        assertEquals("/a", code.redirect)
    }

    @Test
    fun `없거나 이미 쓴 임시 코드는 GONE이다`() {
        every { repository.consumeCode(any(), any()) } returns null

        assertEquals(ErrorCode.GONE, assertFailsWith<BusinessException> { service.consumeCode("tmp_x") }.errorCode)
    }

    @Test
    fun `만료된 행 정리는 지운 개수를 합쳐 돌려준다`() {
        every { repository.deleteExpired(now) } returns (2 to 3)

        assertEquals(5, service.deleteExpired())
    }

    @Test
    fun `redirect는 안전한 상대 경로만 받고 나머지는 슬래시로 바꾼다`() {
        val ok = listOf("/", "/teams", "/teams/team-0123456789ab?tab=1", "/a#b")
        val bad =
            listOf(
                null,
                "",
                "teams",
                "//evil.com",
                "/\\evil.com",
                "https://evil.com",
                "/a\\b",
                "/a\nb",
                "/a\r\nSet-Cookie: x",
                "javascript:alert(1)",
                "/" + "a".repeat(600),
            )

        ok.forEach { assertEquals(it, LoginStateService.sanitizeRedirect(it), it) }
        bad.forEach { assertEquals("/", LoginStateService.sanitizeRedirect(it), it) }
    }
}
