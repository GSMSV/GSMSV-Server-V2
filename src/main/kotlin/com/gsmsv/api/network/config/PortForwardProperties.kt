package com.gsmsv.api.network.config

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * 포트포워딩 설정 (포트포워딩 관리 12번).
 *
 * [publicHost]는 사용자가 접속할 때 쓰는 주소다. 노드를 드러내지 않는 값이어야 하고 운영 값이라 환경 변수로 넣는다.
 * **(미정)** 커스텀 포트 범위([customPortMin]~[customPortMax])는 외부 포트 규칙(23번 4.12)과 함께 인프라 담당이 정한다.
 * 지금 기본값은 개발용 임시 값이고, 운영 값은 환경 변수로 덮어쓴다.
 */
@Validated
@ConfigurationProperties("gsmsv.network.port-forward")
data class PortForwardProperties(
    @field:NotBlank val publicHost: String,
    @field:Min(1) @field:Max(65535) val customPortMin: Int,
    @field:Min(1) @field:Max(65535) val customPortMax: Int,
    /** VM 1대의 커스텀 포트 개수 한도 */
    @field:Positive val customLimit: Int = 30,
) {
    init {
        require(customPortMin <= customPortMax) { "커스텀 포트 범위의 시작이 끝보다 큽니다." }
    }

    val customPortRange: IntRange
        get() = customPortMin..customPortMax
}
