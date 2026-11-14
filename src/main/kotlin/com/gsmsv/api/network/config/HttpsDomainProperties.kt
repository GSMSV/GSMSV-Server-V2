package com.gsmsv.api.network.config

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * HTTPS 도메인 연결 설정 (Network 5.10~5.13).
 *
 * [baseDomain]은 사용자 서브도메인 뒤에 붙는 기본 도메인이다. 운영 값이라 환경 변수로 덮어쓴다.
 * [reservedSubdomains]는 v1 예약어 목록으로 시작한다. **(미정)** 예약어 목록과 원터치 배포와의 이름 공간 공유는 계약이 정하지 않았다.
 */
@Validated
@ConfigurationProperties("gsmsv.network.https")
data class HttpsDomainProperties(
    @field:NotBlank val baseDomain: String,
    @field:Positive val perVmLimit: Int = 2,
    val reservedSubdomains: Set<String> = setOf("admin", "api", "www", "status", "app", "mail", "ftp", "gsmsv"),
)
