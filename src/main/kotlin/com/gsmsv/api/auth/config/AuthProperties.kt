package com.gsmsv.api.auth.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/** 로그인 흐름 설정. 프론트 주소가 없으면 콜백을 보낼 수 없어 기동을 거부한다. */
@Validated
@ConfigurationProperties("gsmsv.auth")
data class AuthProperties(
    @field:NotBlank val frontendUrl: String,
)
