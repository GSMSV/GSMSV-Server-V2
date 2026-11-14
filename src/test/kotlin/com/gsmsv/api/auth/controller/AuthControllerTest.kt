package com.gsmsv.api.auth.controller

import com.gsmsv.api.auth.service.LoginService
import com.gsmsv.api.auth.service.TokenService
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.security.AuthCookieProperties
import com.gsmsv.api.global.security.AuthCookieWriter
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthControllerTest {
    private val controller =
        AuthController(
            mockk<TokenService>(),
            AuthCookieWriter(AuthCookieProperties(secure = false)),
            mockk<LoginService>(),
        )

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
