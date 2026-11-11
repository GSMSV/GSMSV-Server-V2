package com.gsmsv.api.global.ratelimit

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource
import java.time.Duration
import kotlin.test.assertEquals

class RateLimitPropertiesTest {
    @Test
    fun `application yml의 요청 제한 설정이 바인딩된다`() {
        val environment = StandardEnvironment()
        YamlPropertySourceLoader()
            .load("application", ClassPathResource("application.yml"))
            .forEach { environment.propertySources.addLast(it) }

        val properties =
            Binder
                .get(environment)
                .bind("gsmsv.rate-limit", RateLimitProperties::class.java)
                .get()

        val login = properties.rules.first { it.name == "auth-login" }
        assertEquals(3, login.paths.size)
        assertEquals(RateLimitProperties.Key.IP, login.key)
        assertEquals(10, login.limit)
        assertEquals(Duration.ofMinutes(1), login.window)
        assertEquals(60, properties.rules.first { it.key == RateLimitProperties.Key.USER }.limit)
    }
}
