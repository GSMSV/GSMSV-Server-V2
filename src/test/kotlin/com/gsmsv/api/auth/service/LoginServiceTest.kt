package com.gsmsv.api.auth.service

import com.gsmsv.api.auth.client.DataGsmClient
import com.gsmsv.api.auth.client.DataGsmToken
import com.gsmsv.api.auth.config.AuthProperties
import com.gsmsv.api.auth.domain.LoginState
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.user.service.UserService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class LoginServiceTest {
    private val dataGsmClient = mockk<DataGsmClient>()
    private val loginStateService = mockk<LoginStateService>()
    private val service =
        LoginService(
            dataGsmClient,
            loginStateService,
            mockk<UserService>(),
            AuthProperties("https://console.test"),
        )

    @Test
    fun `userinfo UPSTREAM_UNAVAILABLE 예외는 콜백 주소로 전달된다`() {
        every { loginStateService.consumeState("state") } returns LoginState("/", "verifier")
        every { dataGsmClient.exchangeCode("code", "verifier") } returns DataGsmToken("access-token")
        every { dataGsmClient.fetchUserInfo("access-token") } throws
            BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE)

        val location = service.handleCallback("code", "state")

        assertTrue(location.startsWith("https://console.test/login?"), location)
        assertTrue(location.contains("error=UPSTREAM_UNAVAILABLE"), location)
    }
}
