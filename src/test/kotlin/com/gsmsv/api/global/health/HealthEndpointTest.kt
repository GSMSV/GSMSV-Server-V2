package com.gsmsv.api.global.health

import com.gsmsv.api.TestcontainersConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

private const val TEST_SECRET = "dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="

/**
 * 상태 페이지 감시 도구가 보는 헬스 체크 (28번). 로그인 없이 열리고 본문에는 `status`만 있어야 한다.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=$TEST_SECRET"])
@AutoConfigureMockMvc
@Tag("integration")
class HealthEndpointTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `정상이면 인증 없이 200과 status만 돌려준다`() {
        val body =
            mockMvc
                .get("/actuator/health")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.status") { value("UP") }
                }.andReturn()
                .response.contentAsString

        // DB 종류·디스크·컴포넌트 이름 같은 내부 정보가 없어야 한다
        assert(!body.contains("components")) { "components가 응답에 있습니다." }
        assert(!body.contains("details")) { "details가 응답에 있습니다." }
        assert(!body.contains("postgres", ignoreCase = true)) { "DB 종류가 응답에 있습니다." }
    }
}

/** 정상이 아니면 503과 `status: DOWN`만 준다 */
@Import(TestcontainersConfiguration::class, DownIndicatorConfig::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=$TEST_SECRET"])
@AutoConfigureMockMvc
@Tag("integration")
class HealthEndpointDownTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `정상이 아니면 인증 없이 503과 DOWN만 돌려준다`() {
        val body =
            mockMvc
                .get("/actuator/health")
                .andExpect {
                    status { isServiceUnavailable() }
                    jsonPath("$.status") { value("DOWN") }
                }.andReturn()
                .response.contentAsString

        assert(!body.contains("broken-for-test")) { "컴포넌트 이름이 응답에 있습니다." }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class DownIndicatorConfig {
    @Bean
    fun brokenForTestIndicator(): HealthIndicator = HealthIndicator { Health.down().withDetail("secret", "internal").build() }
}
