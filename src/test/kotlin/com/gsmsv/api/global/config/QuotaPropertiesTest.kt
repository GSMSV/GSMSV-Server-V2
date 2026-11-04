package com.gsmsv.api.global.config

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.validation.BindValidationException
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuotaPropertiesTest {
    @EnableConfigurationProperties(QuotaProperties::class)
    class Config

    // application.yml을 읽어 실제 기동과 같은 값으로 바인딩한다
    private val runner =
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Config::class.java)

    @Test
    fun `기본값은 정책 문서의 한도와 같다`() {
        runner.run { context ->
            val quota = context.getBean(QuotaProperties::class.java)

            assertEquals(ResourceQuota(vcpu = 4, ramGib = 8, ssdGib = 30), quota.personal)
            assertEquals(ResourceQuota(vcpu = 8, ramGib = 16, ssdGib = 60), quota.team)
        }
    }

    @Test
    fun `설정으로 한도를 바꿀 수 있다`() {
        runner
            .withPropertyValues("gsmsv.quota.team.vcpu=12", "gsmsv.quota.personal.ram-gib=6")
            .run { context ->
                val quota = context.getBean(QuotaProperties::class.java)

                assertEquals(12, quota.team.vcpu)
                assertEquals(6, quota.personal.ramGib)
            }
    }

    @Test
    fun `0 이하 한도면 기동에 실패한다`() {
        runner
            .withPropertyValues("gsmsv.quota.personal.ssd-gib=0")
            .run { context ->
                val causes = generateSequence(context.startupFailure) { it.cause }
                assertTrue(causes.any { it is BindValidationException })
            }
    }

    @Test
    fun `한도 값이 빠지면 기동에 실패한다`() {
        ApplicationContextRunner()
            .withUserConfiguration(Config::class.java)
            .withPropertyValues("gsmsv.quota.personal.vcpu=4")
            .run { context -> assertNotNull(context.startupFailure) }
    }
}
