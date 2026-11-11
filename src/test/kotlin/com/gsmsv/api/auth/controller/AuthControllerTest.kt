package com.gsmsv.api.auth.controller

import com.gsmsv.api.auth.service.LoginService
import com.gsmsv.api.support.ApiWebMvcTest
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@ApiWebMvcTest(AuthController::class)
class AuthControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var loginService: LoginService

    @Test
    fun `인증 없이 로그인을 시작하면 DataGSM 주소로 302 한다`() {
        every { loginService.startLogin("/teams") } returns "https://oauth.test/authorize?state=s"

        mockMvc.get("/api/v2/auth/datagsm/authorize?redirect=/teams").andExpect {
            status { isFound() }
            header { string("Location", "https://oauth.test/authorize?state=s") }
            header { string("Cache-Control", "no-store") }
        }
    }

    @Test
    fun `redirect가 없어도 로그인을 시작한다`() {
        every { loginService.startLogin(null) } returns "https://oauth.test/authorize?state=s"

        mockMvc.get("/api/v2/auth/datagsm/authorize").andExpect { status { isFound() } }
    }

    @Test
    fun `인증 없이 부른 콜백은 성공하면 프론트로 302 한다`() {
        every { loginService.handleCallback("c", "s") } returns "https://console.test/login/callback?code=tmp_x"

        mockMvc.get("/api/v2/auth/datagsm/callback?code=c&state=s").andExpect {
            status { isFound() }
            header { string("Location", "https://console.test/login/callback?code=tmp_x") }
        }
    }

    @Test
    fun `콜백 파라미터가 빠져도 400 JSON이 아니라 서비스에 넘겨 302 처리한다`() {
        every { loginService.handleCallback(null, null) } returns "https://console.test/login?error=OAUTH_STATE_INVALID"

        mockMvc.get("/api/v2/auth/datagsm/callback").andExpect {
            status { isFound() }
            header { string("Location", "https://console.test/login?error=OAUTH_STATE_INVALID") }
        }
    }
}
