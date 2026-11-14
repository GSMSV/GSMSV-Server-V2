package com.gsmsv.api.auth.controller

import com.gsmsv.api.auth.service.LoginService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AuthControllerTest {
    private val loginService = mockk<LoginService>()
    private val controller = AuthController(loginService)

    @Test
    fun `authorize는 외부 주소 redirect를 루트 경로로 정리한다`() {
        every { loginService.startLogin("/") } returns "https://datagsm.test/authorize?state=state"

        val response = controller.authorize("https://attacker.test/path")

        assertEquals("https://datagsm.test/authorize?state=state", response.headers.location.toString())
        verify(exactly = 1) { loginService.startLogin("/") }
    }
}
