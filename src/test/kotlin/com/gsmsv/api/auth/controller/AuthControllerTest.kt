package com.gsmsv.api.auth.controller

import com.gsmsv.api.auth.service.LoginService
import com.gsmsv.api.auth.service.TokenService
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.security.AuthCookieProperties
import com.gsmsv.api.global.security.AuthCookieWriter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthControllerTest {
    private val loginService = mockk<LoginService>()
    private val tokenService = mockk<TokenService>()

    // 쿠키 삭제 헤더를 그대로 확인하려고 실제 구현을 쓴다
    private val controller = AuthController(tokenService, AuthCookieWriter(AuthCookieProperties(secure = false)), loginService)

    @Test
    fun `authorize는 외부 주소 redirect를 루트 경로로 정리한다`() {
        every { loginService.startLogin("/") } returns "https://datagsm.test/authorize?state=state"

        val response = controller.authorize("https://attacker.test/path")

        assertEquals("https://datagsm.test/authorize?state=state", response.headers.location.toString())
        verify(exactly = 1) { loginService.startLogin("/") }
    }

    @Test
    fun `refresh 실패 시 오류 응답 헤더에 쿠키 삭제를 담는다`() {
        val exception = assertFailsWith<BusinessException> { controller.refresh(null) }

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.errorCode)
        val setCookies = exception.responseHeaders[HttpHeaders.SET_COOKIE].orEmpty()
        assertEquals(2, setCookies.size)
        assert(setCookies.any { it.startsWith("access_token=") && it.contains("Max-Age=0") })
        assert(setCookies.any { it.startsWith("refresh_token=") && it.contains("Max-Age=0") })
    }
}
