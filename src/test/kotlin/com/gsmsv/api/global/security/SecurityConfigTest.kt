package com.gsmsv.api.global.security

import com.gsmsv.api.global.config.RequestIdFilter
import com.gsmsv.api.global.security.dev.DevSecurityConfig
import com.gsmsv.api.global.security.dev.DevTokenController
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper

@WebMvcTest(
    controllers = [SecurityConfigTest.TestController::class, DevTokenController::class],
    properties = ["gsmsv.security.jwt.secret=${AccessTokenProviderTest.TEST_SECRET}"],
)
@EnableConfigurationProperties(JwtProperties::class)
@Import(
    SecurityConfigTest.TestController::class,
    SecurityConfig::class,
    DevSecurityConfig::class,
    AccessTokenProvider::class,
    SecurityErrorResponder::class,
    RequestIdFilter::class,
)
class SecurityConfigTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokenProvider: AccessTokenProvider,
    @Autowired private val jsonMapper: JsonMapper,
) {
    @RestController
    class TestController {
        @GetMapping("/test/me")
        fun me(
            @CurrentUser user: AuthenticatedUser,
        ): AuthenticatedUser = user

        @PostMapping("/test/me")
        fun change(
            @CurrentUser user: AuthenticatedUser,
        ): AuthenticatedUser = user
    }

    private val token by lazy { tokenProvider.issue(USER_ID, Role.USER).value }

    @Test
    fun `Bearer 토큰이 맞으면 @CurrentUser로 사용자를 받는다`() {
        mockMvc
            .get("/test/me") { header("Authorization", "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.user_id") { value(USER_ID) }
                jsonPath("$.role") { value("USER") }
            }
    }

    @Test
    fun `쿠키의 토큰으로도 인증한다`() {
        mockMvc
            .get("/test/me") { cookie(Cookie("access_token", token)) }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `다른 사이트에서 온 요청에는 쿠키의 토큰을 쓰지 않는다`() {
        mockMvc
            .post("/test/me") {
                cookie(Cookie("access_token", token))
                header("Sec-Fetch-Site", "cross-site")
            }.andExpectUnauthenticated()
    }

    @Test
    fun `토큰이 없으면 401 UNAUTHENTICATED를 공통 오류 형식으로 돌려준다`() {
        mockMvc
            .get("/test/me") { header("X-Request-Id", "req-1") }
            .andExpectUnauthenticated()
            .andExpect { jsonPath("$.request_id") { value("req-1") } }
    }

    @Test
    fun `틀린 토큰이면 401을 돌려준다`() {
        listOf("Bearer abc.def.ghi", "Bearer ${token}x", "Basic dXNlcjpwdw==", "Bearer ").forEach { authorization ->
            mockMvc.get("/test/me") { header("Authorization", authorization) }.andExpectUnauthenticated()
        }
    }

    @Test
    fun `없는 경로도 인증 전에는 401을 돌려준다`() {
        mockMvc.get("/api/v2/unknown").andExpectUnauthenticated()
    }

    @Test
    fun `헬스 체크는 인증 없이 연다`() {
        // 슬라이스 테스트에는 actuator가 없어 404가 난다. 401이 아니면 인증 필터를 통과한 것이다
        mockMvc.get("/actuator/health").andExpect { status { isNotFound() } }
    }

    @Test
    fun `개발용 토큰 API는 인증 없이 토큰을 발급한다`() {
        val body =
            mockMvc
                .post("/api/v2/dev/token") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"user_id":"$USER_ID","role":"ADMIN"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.token_type") { value("Bearer") }
                    jsonPath("$.expires_in") { value(900) }
                }.andReturn()
                .response.contentAsString
        val issued = jsonMapper.readTree(body).get("access_token").asString()

        mockMvc
            .get("/test/me") { header("Authorization", "Bearer $issued") }
            .andExpect { jsonPath("$.role") { value("ADMIN") } }
    }

    @Test
    fun `개발용 토큰 API는 사용자 id 형식을 검사한다`() {
        mockMvc
            .post("/api/v2/dev/token") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"user_id":"team-4p8w2k9m1xza","role":"USER"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_RESOURCE_ID") }
            }
    }

    private fun ResultActionsDsl.andExpectUnauthenticated(): ResultActionsDsl =
        andExpect {
            status { isUnauthorized() }
            header { string("WWW-Authenticate", "Bearer") }
            jsonPath("$.code") { value("UNAUTHENTICATED") }
            jsonPath("$.message") { isNotEmpty() }
            jsonPath("$.request_id") { isNotEmpty() }
        }

    private companion object {
        const val USER_ID = AccessTokenProviderTest.USER_ID
    }
}
