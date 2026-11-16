package com.gsmsv.api.infra.config

import com.gsmsv.api.infra.network.FakeNetworkInfraClient
import com.gsmsv.api.infra.network.HttpNetworkInfraClient
import com.gsmsv.api.infra.network.NetworkInfraClient
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InfraConfigTest {
    @EnableConfigurationProperties(InfraProperties::class)
    class PropertiesConfig

    private fun runner(vararg profiles: String) =
        ApplicationContextRunner()
            .withUserConfiguration(
                PropertiesConfig::class.java,
                InfraRestClientConfig::class.java,
                FakeNetworkInfraClient::class.java,
                HttpNetworkInfraClient::class.java,
            ).withBean(RestClient.Builder::class.java, { RestClient.builder() })
            .withInitializer { it.environment.setActiveProfiles(*profiles) }

    @Test
    fun `local 프로필에서는 설정 없이 모의 구현이 뜬다`() {
        runner("local").run { context ->
            assertIs<FakeNetworkInfraClient>(context.getBean(NetworkInfraClient::class.java))
            assertTrue(context.getBeansOfType(RestClient::class.java).isEmpty())
        }
    }

    @Test
    fun `운영 프로필에서는 설정이 있으면 실제 클라이언트가 뜬다`() {
        runner("prod")
            .withPropertyValues("gsmsv.infra.base-url=http://go.test", "gsmsv.infra.shared-secret=test-secret")
            .run { context ->
                assertIs<HttpNetworkInfraClient>(context.getBean(NetworkInfraClient::class.java))
                assertNotNull(context.getBean(RestClient::class.java))
            }
    }

    @Test
    fun `운영 프로필에서 주소나 시크릿이 비어 있으면 기동에 실패한다`() {
        runner("prod").run { context -> assertNotNull(context.startupFailure) }
        runner("prod")
            .withPropertyValues("gsmsv.infra.base-url=http://go.test")
            .run { context -> assertNotNull(context.startupFailure) }
    }

    @Test
    fun `설정의 toString에 시크릿이 나오지 않는다`() {
        val properties = InfraProperties(baseUrl = "http://go.test", sharedSecret = "test-secret")

        assertFalse(properties.toString().contains("test-secret"))
        assertEquals("X-Internal-Secret", properties.secretHeader)
    }
}
