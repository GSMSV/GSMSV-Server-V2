package com.gsmsv.api.global.ratelimit

import com.gsmsv.api.auth.config.AuthProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

@Configuration
class RateLimitConfig {
    @Bean
    fun rateLimiter(): RateLimiter = RateLimiter()

    @Bean
    fun rateLimitFilter(
        properties: RateLimitProperties,
        limiter: RateLimiter,
        jsonMapper: JsonMapper,
        authProperties: AuthProperties,
    ): RateLimitFilter = RateLimitFilter(properties, limiter, jsonMapper, authProperties)

    // 필터 빈은 Boot가 서블릿 필터로도 등록한다. 보안 체인 안에서 한 번만 돌게 끈다
    @Bean
    fun rateLimitFilterRegistration(filter: RateLimitFilter): FilterRegistrationBean<RateLimitFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }
}
