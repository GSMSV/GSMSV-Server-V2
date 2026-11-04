package com.gsmsv.api.global.security.dev

import com.gsmsv.api.global.security.statelessApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

/** 개발용 토큰 API만 인증 없이 연다. 기본 필터 체인보다 먼저 이 경로를 가져간다 */
@Configuration
@Profile(DevProfiles.DEV, DevProfiles.LOCAL)
class DevSecurityConfig {
    @Bean
    @Order(1)
    fun devTokenFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher(DevProfiles.TOKEN_PATH)
            .statelessApi()
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
}

object DevProfiles {
    const val DEV = "dev"
    const val LOCAL = "local"
    const val TOKEN_PATH = "/api/v2/dev/token"
}
