package com.gsmsv.api.global.security

import com.gsmsv.api.global.security.dev.DevSecurityConfig
import com.gsmsv.api.global.security.dev.DevTokenController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(
    controllers = [DevTokenController::class],
    properties = ["gsmsv.security.jwt.secret=${AccessTokenProviderTest.TEST_SECRET}"],
)
@EnableConfigurationProperties(JwtProperties::class)
@Import(SecurityConfig::class, DevSecurityConfig::class, AccessTokenProvider::class, SecurityErrorResponder::class)
@ActiveProfiles("prod")
class DevTokenProdProfileTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `운영 프로필에서는 개발용 토큰 API가 열리지 않는다`() {
        mockMvc
            .post("/api/v2/dev/token") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"user_id":"${AccessTokenProviderTest.USER_ID}","role":"ADMIN"}"""
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("UNAUTHENTICATED") }
            }
    }
}
