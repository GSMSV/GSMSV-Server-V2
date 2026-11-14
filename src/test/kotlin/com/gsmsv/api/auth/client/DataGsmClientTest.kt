package com.gsmsv.api.auth.client

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataGsmClientTest {
    private val properties =
        DataGsmProperties(
            clientId = "test-client",
            clientSecret = "test-secret",
            redirectUri = "https://api.test/api/v2/auth/datagsm/callback",
            authBaseUrl = "http://auth.test",
            resourceBaseUrl = "http://resource.test",
        )
    private val authBuilder = RestClient.builder().baseUrl(properties.authBaseUrl)
    private val resourceBuilder = RestClient.builder().baseUrl(properties.resourceBaseUrl)
    private val authServer = MockRestServiceServer.bindTo(authBuilder).build()
    private val resourceServer = MockRestServiceServer.bindTo(resourceBuilder).build()
    private val client = DataGsmClient(properties, authBuilder.build(), resourceBuilder.build())

    @Test
    fun `인가 코드를 form으로 보내 access_token을 받는다`() {
        authServer
            .expect(requestTo("http://auth.test/v1/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Content-Type", "application/x-www-form-urlencoded"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("code=abc")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=test-client")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=test-secret")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("redirect_uri=")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("code_verifier=verifier-value")))
            .andRespond(
                withSuccess(
                    """{"access_token":"at","token_type":"Bearer","expires_in":3600,"refresh_token":"rt","scope":"x","id_token":null}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals("at", client.exchangeCode("abc", "verifier-value").accessToken)
        authServer.verify()
    }

    @Test
    fun `토큰의 toString에는 토큰 값이 없다`() {
        assertFalse(DataGsmToken("secret-token").toString().contains("secret-token"))
    }

    @Test
    fun `기존 코드 교환은 verifier 없이 동작한다`() {
        authServer
            .expect(requestTo("http://auth.test/v1/oauth/token"))
            .andExpect(
                content().string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("code_verifier")),
                ),
            ).andRespond(withSuccess("""{"access_token":"at"}""", MediaType.APPLICATION_JSON))

        assertEquals("at", client.exchangeCode("abc").accessToken)
        authServer.verify()
    }

    @Test
    fun `코드가 거절되면 OAUTH_EXCHANGE_FAILED다`() {
        authServer
            .expect(requestTo("http://auth.test/v1/oauth/token"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("""{"error":"invalid_grant"}"""))

        assertEquals(ErrorCode.OAUTH_EXCHANGE_FAILED, assertFailsWith<BusinessException> { client.exchangeCode("bad") }.errorCode)
    }

    @Test
    fun `client_secret이 틀려 401이어도 OAUTH_EXCHANGE_FAILED다`() {
        authServer.expect(requestTo("http://auth.test/v1/oauth/token")).andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        assertEquals(ErrorCode.OAUTH_EXCHANGE_FAILED, assertFailsWith<BusinessException> { client.exchangeCode("x") }.errorCode)
    }

    @Test
    fun `DataGSM이 5xx면 UPSTREAM_UNAVAILABLE이다`() {
        authServer.expect(requestTo("http://auth.test/v1/oauth/token")).andRespond(withStatus(HttpStatus.BAD_GATEWAY))

        val e = assertFailsWith<BusinessException> { client.exchangeCode("x") }

        assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, e.errorCode)
        assertEquals("학생 정보 서비스가 응답하지 않습니다.", e.message)
    }

    @Test
    fun `요청 한도 429는 RATE_LIMITED이다`() {
        authServer.expect(requestTo("http://auth.test/v1/oauth/token")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))

        assertEquals(ErrorCode.RATE_LIMITED, assertFailsWith<BusinessException> { client.exchangeCode("x") }.errorCode)
    }

    @Test
    fun `연결 실패와 타임아웃은 UPSTREAM_UNAVAILABLE이다`() {
        authServer
            .expect(
                requestTo("http://auth.test/v1/oauth/token"),
            ).andRespond { throw ResourceAccessException("timeout", IOException()) }

        assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, assertFailsWith<BusinessException> { client.exchangeCode("x") }.errorCode)
    }

    @Test
    fun `깨진 응답은 UPSTREAM_UNAVAILABLE이다`() {
        authServer.expect(requestTo("http://auth.test/v1/oauth/token")).andRespond(withSuccess("<html>", MediaType.TEXT_HTML))

        assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, assertFailsWith<BusinessException> { client.exchangeCode("x") }.errorCode)
    }

    @Test
    fun `userinfo를 Bearer로 조회하고 모르는 필드는 무시한다`() {
        resourceServer
            .expect(requestTo("http://resource.test/userinfo"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer at"))
            .andRespond(withSuccess(STUDENT_JSON.replace("GENERAL_STUDENT", "STUDENT_COUNCIL"), MediaType.APPLICATION_JSON))

        val info = client.fetchUserInfo("at")

        assertEquals(10L, info.id)
        assertEquals("s26022@gsm.hs.kr", info.email)
        assertEquals("USER", info.role)
        assertEquals("ACTIVE", info.status)
        assertEquals(26022, info.student?.studentNumber)
        assertEquals(2, info.student?.classNum)
        assertTrue(info.isEnrolled())
    }

    @Test
    fun `졸업생과 자퇴생은 재학이 아니다`() {
        for (role in listOf("GRADUATE", "WITHDRAWN", "UNKNOWN_ROLE")) {
            resourceServer.reset()
            resourceServer
                .expect(requestTo("http://resource.test/userinfo"))
                .andRespond(withSuccess(STUDENT_JSON.replace("GENERAL_STUDENT", role), MediaType.APPLICATION_JSON))

            assertFalse(client.fetchUserInfo("at").isEnrolled(), role)
        }
    }

    @Test
    fun `문서화된 재학생 역할만 재학으로 판단한다`() {
        for (role in listOf("GENERAL_STUDENT", "STUDENT_COUNCIL", "DORMITORY_MANAGER")) {
            resourceServer.reset()
            resourceServer
                .expect(requestTo("http://resource.test/userinfo"))
                .andRespond(withSuccess(STUDENT_JSON.replace("GENERAL_STUDENT", role), MediaType.APPLICATION_JSON))

            assertTrue(client.fetchUserInfo("at").isEnrolled(), role)
        }
    }

    @Test
    fun `교사이거나 학생 정보가 없으면 재학이 아니다`() {
        resourceServer
            .expect(requestTo("http://resource.test/userinfo"))
            .andRespond(
                withSuccess(
                    """{"id":1,"email":"t@gsm.hs.kr","role":"ADMIN","status":"ACTIVE","objectType":"TEACHER","student":null,"teacher":{"id":3,"name":"교사","email":"t@gsm.hs.kr","department":"GRADE"}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val info = client.fetchUserInfo("at")

        assertFalse(info.isEnrolled())
        assertNull(info.student)
        assertEquals(3L, info.teacher?.id)
    }

    @Test
    fun `userinfo가 401이면 OAUTH_EXCHANGE_FAILED, 5xx면 UPSTREAM_UNAVAILABLE이다`() {
        resourceServer.expect(requestTo("http://resource.test/userinfo")).andRespond(withStatus(HttpStatus.UNAUTHORIZED))
        assertEquals(ErrorCode.OAUTH_EXCHANGE_FAILED, assertFailsWith<BusinessException> { client.fetchUserInfo("at") }.errorCode)

        resourceServer.reset()
        resourceServer.expect(requestTo("http://resource.test/userinfo")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, assertFailsWith<BusinessException> { client.fetchUserInfo("at") }.errorCode)
    }

    @Test
    fun `로그인 화면 주소에 클라이언트와 state와 범위가 들어간다`() {
        val uri = client.authorizationUri("state-1")

        assertTrue(uri.startsWith("http://auth.test/v1/oauth/authorize?"))
        assertTrue(uri.contains("client_id=test-client"))
        assertTrue(uri.contains("state=state-1"))
        assertTrue(uri.contains("response_type=code"))
        assertTrue(uri.contains("redirect_uri=https%3A%2F%2Fapi.test%2Fapi%2Fv2%2Fauth%2Fdatagsm%2Fcallback"), uri)
        assertFalse(uri.contains("code_challenge"))
        // 쿼리의 ':'는 RFC 3986상 허용 문자라 그대로 두고, 범위 사이 공백만 %20이 된다
        assertTrue(uri.contains("scope=datagsm:account_read%20datagsm:student_read"))
        assertFalse(uri.contains("test-secret"))
    }

    @Test
    fun `PKCE 인가 요청은 verifier와 S256 challenge를 만든다`() {
        val request = client.authorizationRequest("state-1")
        val expectedChallenge =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(request.codeVerifier.toByteArray(Charsets.US_ASCII)))

        assertEquals(43, request.codeVerifier.length)
        assertTrue(request.authorizationUri.contains("code_challenge=$expectedChallenge"))
        assertTrue(request.authorizationUri.contains("code_challenge_method=S256"))
        assertFalse(request.toString().contains(request.codeVerifier))
    }

    @Test
    fun `설정 toString에는 secret이 없다`() {
        assertFalse(properties.toString().contains("test-secret"))
    }

    private companion object {
        const val STUDENT_JSON =
            """{"sub":"s26022@gsm.hs.kr","id":10,"email":"s26022@gsm.hs.kr","role":"USER","status":"ACTIVE","objectType":"STUDENT","isStudent":true,"student":{"id":77,"name":"홍길동","sex":"MAN","email":"s26022@gsm.hs.kr","grade":2,"classNum":2,"number":2,"studentNumber":26022,"major":"SW_DEVELOPMENT","role":"GENERAL_STUDENT","dormitoryFloor":3,"dormitoryRoom":301},"teacher":null,"clubs":[],"projects":[]}"""
    }
}
