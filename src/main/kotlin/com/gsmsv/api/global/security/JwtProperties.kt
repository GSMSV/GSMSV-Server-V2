package com.gsmsv.api.global.security

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

/**
 * 이 서버가 발급하는 액세스 토큰(HS256 JWT) 설정.
 *
 * [secret]은 Base64로 인코딩한 32바이트 이상의 무작위 값이고 환경 변수로만 받는다.
 * [accessTokenTtl]은 졸업 반영이 늦어도 이 시간 안에 끊기는 기준이라 짧게 둔다 (Contracts-V2 `policies/feature-policies.md` 1번).
 */
@Validated
@ConfigurationProperties("gsmsv.security.jwt")
data class JwtProperties(
    @field:NotBlank val secret: String,
    val accessTokenTtl: Duration,
)
