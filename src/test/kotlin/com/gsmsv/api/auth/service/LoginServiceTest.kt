package com.gsmsv.api.auth.service

import com.gsmsv.api.auth.client.DataGsmClient
import com.gsmsv.api.auth.client.DataGsmStudent
import com.gsmsv.api.auth.client.DataGsmToken
import com.gsmsv.api.auth.client.DataGsmUserInfo
import com.gsmsv.api.auth.config.AuthProperties
import com.gsmsv.api.auth.domain.LoginState
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.domain.UserStatus
import com.gsmsv.api.user.service.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LoginServiceTest {
    private val client = mockk<DataGsmClient>()
    private val stateService = mockk<LoginStateService>()
    private val userService = mockk<UserService>()
    private val service = LoginService(client, stateService, userService, AuthProperties("https://console.test"))

    private fun info(
        role: String = "GENERAL_STUDENT",
        email: String? = "s26022@gsm.hs.kr",
    ) = DataGsmUserInfo(
        id = 10,
        email = email,
        role = "USER",
        status = "ACTIVE",
        objectType = "STUDENT",
        student = DataGsmStudent(77, "홍길동", email, 2, 2, 2, 26022, role),
    )

    private fun user(
        role: Role = Role.USER,
        status: UserStatus = UserStatus.ACTIVE,
    ) = User(id = "user-0123456789ab", dataGsmId = "10", email = "s26022@gsm.hs.kr", name = "홍길동", role = role, status = status)

    private fun givenDataGsm(info: DataGsmUserInfo) {
        every { stateService.consumeState("state-1") } returns LoginState("/teams")
        every { client.exchangeCode("code-1") } returns DataGsmToken("at")
        every { client.fetchUserInfo("at") } returns info
    }

    private fun givenAccount(
        existing: User?,
        saved: User = user(),
    ) {
        every { userService.findByDataGsmId("10") } returns existing
        every { userService.findOrCreateFromDataGsm("10", any(), any(), any(), any(), any()) } returns saved
        every { stateService.issueCode(saved.id, "/teams") } returns "tmp_abc"
    }

    @Test
    fun `로그인 시작은 state를 만들어 DataGSM 로그인 주소를 돌려준다`() {
        every { stateService.issueState("/teams") } returns "state-1"
        every { client.authorizationUri("state-1") } returns "https://oauth.test/authorize?state=state-1"

        assertEquals("https://oauth.test/authorize?state=state-1", service.startLogin("/teams"))
    }

    @Test
    fun `재학생이 처음 로그인하면 계정을 만들고 임시 코드를 프론트로 넘긴다`() {
        givenDataGsm(info())
        givenAccount(existing = null)

        assertEquals("https://console.test/login/callback?code=tmp_abc", service.handleCallback("code-1", "state-1"))
        verify { userService.findOrCreateFromDataGsm("10", "s26022@gsm.hs.kr", "홍길동", 2, 2, 26022) }
    }

    @Test
    fun `졸업생과 자퇴생은 NOT_ENROLLED다`() {
        for (role in listOf("GRADUATE", "WITHDRAWN")) {
            givenDataGsm(info(role))
            givenAccount(existing = null)

            assertEquals("https://console.test/login?error=NOT_ENROLLED", service.handleCallback("code-1", "state-1"), role)
        }
        verify(exactly = 0) { stateService.issueCode(any(), any()) }
    }

    @Test
    fun `관리자는 졸업해도 로그인할 수 있다`() {
        val admin = user(role = Role.ADMIN)
        givenDataGsm(info("GRADUATE"))
        givenAccount(existing = admin, saved = admin)

        assertEquals("https://console.test/login/callback?code=tmp_abc", service.handleCallback("code-1", "state-1"))
    }

    @Test
    fun `비활성 계정은 ACCOUNT_INACTIVE다`() {
        givenDataGsm(info())
        givenAccount(existing = user(status = UserStatus.INACTIVE))

        assertEquals("https://console.test/login?error=ACCOUNT_INACTIVE", service.handleCallback("code-1", "state-1"))
        verify(exactly = 0) { userService.findOrCreateFromDataGsm(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `state가 없거나 틀리면 DataGSM을 부르지 않고 OAUTH_STATE_INVALID다`() {
        every { stateService.consumeState("bad") } throws BusinessException(ErrorCode.OAUTH_STATE_INVALID)

        assertEquals("https://console.test/login?error=OAUTH_STATE_INVALID", service.handleCallback("code-1", "bad"))
        assertEquals("https://console.test/login?error=OAUTH_STATE_INVALID", service.handleCallback("code-1", null))
        verify(exactly = 0) { client.exchangeCode(any()) }
    }

    @Test
    fun `code 없이 돌아오면 state는 소모하고 OAUTH_EXCHANGE_FAILED다`() {
        every { stateService.consumeState("state-1") } returns LoginState("/")

        assertEquals("https://console.test/login?error=OAUTH_EXCHANGE_FAILED", service.handleCallback(null, "state-1"))
        verify(exactly = 1) { stateService.consumeState("state-1") }
    }

    @Test
    fun `코드 교환이 거절되면 OAUTH_EXCHANGE_FAILED, DataGSM 장애면 UPSTREAM_UNAVAILABLE이다`() {
        every { stateService.consumeState("state-1") } returns LoginState("/")
        every { client.exchangeCode("code-1") } throws BusinessException(ErrorCode.OAUTH_EXCHANGE_FAILED)
        assertEquals("https://console.test/login?error=OAUTH_EXCHANGE_FAILED", service.handleCallback("code-1", "state-1"))

        every { client.exchangeCode("code-1") } throws BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE)
        assertEquals("https://console.test/login?error=UPSTREAM_UNAVAILABLE", service.handleCallback("code-1", "state-1"))
    }

    @Test
    fun `이메일을 받지 못하면 OAUTH_EXCHANGE_FAILED다`() {
        givenDataGsm(info(email = null))

        assertEquals("https://console.test/login?error=OAUTH_EXCHANGE_FAILED", service.handleCallback("code-1", "state-1"))
    }

    @Test
    fun `예상하지 못한 오류도 JSON이 아니라 프론트 오류 주소로 보낸다`() {
        every { stateService.consumeState("state-1") } throws IllegalStateException("db down")

        assertEquals("https://console.test/login?error=OAUTH_EXCHANGE_FAILED", service.handleCallback("code-1", "state-1"))
    }

    @Test
    fun `계약에 없는 오류 코드는 프론트에 그대로 내보내지 않는다`() {
        every { stateService.consumeState("state-1") } throws BusinessException(ErrorCode.INTERNAL)

        assertEquals("https://console.test/login?error=OAUTH_EXCHANGE_FAILED", service.handleCallback("code-1", "state-1"))
    }
}
