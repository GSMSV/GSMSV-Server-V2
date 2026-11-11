package com.gsmsv.api.auth.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * 로그인 흐름 설정.
 *
 * [frontendUrl]은 DataGSM 콜백이 끝난 뒤 브라우저를 돌려보낼 프론트 주소다 (예: `https://console.example.com`).
 * 환경 변수로만 받고 기본값을 두지 않으며, 비어 있으면 기동에 실패한다.
 */
@Validated
@ConfigurationProperties("gsmsv.auth")
data class AuthProperties(
    @field:NotBlank val frontendUrl: String,
)
