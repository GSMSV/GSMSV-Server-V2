package com.gsmsv.api.auth.client

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DataGsmPropertiesTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(
                org.springframework.boot.autoconfigure.AutoConfigurations
                    .of(ValidationAutoConfiguration::class.java),
            ).withUserConfiguration(Config::class.java)

    @EnableConfigurationProperties(DataGsmProperties::class)
    class Config

    @Test
    fun `세 값이 모두 있으면 바인딩된다`() {
        runner
            .withPropertyValues(
                "gsmsv.datagsm.client-id=id",
                "gsmsv.datagsm.client-secret=secret",
                "gsmsv.datagsm.redirect-uri=https://api.test/callback",
            ).run {
                assertNotNull(it.getBean(DataGsmProperties::class.java))
                assertEquals("id", it.getBean(DataGsmProperties::class.java).clientId)
            }
    }

    @Test
    fun `값이 비어 있으면 기동에 실패한다`() {
        for (blank in listOf("client-id", "client-secret", "redirect-uri")) {
            val values =
                mutableMapOf("client-id" to "id", "client-secret" to "secret", "redirect-uri" to "https://api.test/callback")
            values[blank] = ""

            runner.withPropertyValues(*values.map { "gsmsv.datagsm.${it.key}=${it.value}" }.toTypedArray()).run {
                assertNotNull(it.startupFailure, "$blank 가 비었는데 기동됨")
            }
        }
    }

    @Test
    fun `값이 없으면 기동에 실패한다`() {
        runner.run { assertNotNull(it.startupFailure) }
    }
}
