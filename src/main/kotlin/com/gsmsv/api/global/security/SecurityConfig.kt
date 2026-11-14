package com.gsmsv.api.global.security

import com.gsmsv.api.global.ratelimit.RateLimitFilter
import jakarta.servlet.DispatcherType
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 인증 필터 설정. [PUBLIC_PATHS] 밖의 모든 요청은 유효한 액세스 토큰이 있어야 한다 (기본 거부, Contracts-V2 `rules/authorization.md`).
 *
 * 여기서는 "누구인가"만 확인한다. 자원에 대한 권한 판단은 `global.authorization`의 로더가 한다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig : WebMvcConfigurer {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        tokenProvider: AccessTokenProvider,
        errorResponder: SecurityErrorResponder,
        rateLimitFilter: ObjectProvider<RateLimitFilter>,
    ): SecurityFilterChain =
        http
            .statelessApi()
            .authorizeHttpRequests {
                // 오류 응답을 만드는 내부 재요청이 401로 바뀌지 않게 연다
                it.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                it.requestMatchers(*PUBLIC_PATHS).permitAll()
                it.anyRequest().authenticated()
            }.oauth2ResourceServer {
                it.bearerTokenResolver(AccessTokenResolver())
                it.authenticationEntryPoint(errorResponder)
                it.accessDeniedHandler(errorResponder)
                it.jwt { jwt ->
                    jwt.decoder(tokenProvider.decoder)
                    jwt.jwtAuthenticationConverter(tokenProvider::toAuthentication)
                }
            }.apply {
                // 사용자 키 제한은 인증 결과가 필요해 토큰 검증 필터 뒤에 둔다
                rateLimitFilter.ifAvailable?.let { addFilterAfter(it, BearerTokenAuthenticationFilter::class.java) }
            }.exceptionHandling {
                it.authenticationEntryPoint(errorResponder)
                it.accessDeniedHandler(errorResponder)
            }.build()

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(CurrentUserArgumentResolver())
    }

    companion object {
        /** 인증 없이 여는 경로. 개발용 토큰 API는 [com.gsmsv.api.global.security.dev.DevSecurityConfig]가 따로 연다 */
        val PUBLIC_PATHS = arrayOf("/actuator/health", "/api/v2/auth/datagsm/authorize", "/api/v2/auth/datagsm/callback")
    }
}

/**
 * 세션·폼 로그인 없이 토큰으로만 인증하는 API의 공통 설정.
 *
 * CSRF 검사는 끈다. 쿠키로 받은 토큰은 [AccessTokenResolver]가 다른 사이트의 요청에 쓰지 않는 것으로 막는다.
 */
internal fun HttpSecurity.statelessApi(): HttpSecurity =
    csrf { it.disable() }
        .formLogin { it.disable() }
        .httpBasic { it.disable() }
        .logout { it.disable() }
        .requestCache { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
