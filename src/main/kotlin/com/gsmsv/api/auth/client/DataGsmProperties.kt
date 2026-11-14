package com.gsmsv.api.auth.client

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

/**
 * DataGSM OAuth 연동 설정 (https://docs.datagsm.kr/oauth).
 *
 * [clientId]·[clientSecret]·[redirectUri]는 환경 변수로만 받고 기본값을 두지 않는다.
 * 비어 있으면 기동에 실패한다. 검증을 건너뛰는 상태로 뜨지 않게 하기 위해서다.
 * 주소(인증 서버·사용자 데이터 서버)는 공개된 값이라 기본값을 두되, 테스트나 전환을 위해 바꿀 수 있다.
 */
@Validated
@ConfigurationProperties("gsmsv.datagsm")
data class DataGsmProperties(
    @field:NotBlank val clientId: String,
    @field:NotBlank val clientSecret: String,
    @field:NotBlank val redirectUri: String,
    val authBaseUrl: String = "https://oauth.authorization.datagsm.kr",
    val resourceBaseUrl: String = "https://oauth.resource.datagsm.kr",
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val readTimeout: Duration = Duration.ofSeconds(5),
) {
    // 시크릿이 로그나 예외 메시지에 찍히지 않게 한다
    override fun toString(): String = "DataGsmProperties(clientId=$clientId, redirectUri=$redirectUri)"
}
