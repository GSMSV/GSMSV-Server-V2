package com.gsmsv.api.global.ratelimit

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

/**
 * 요청 제한 설정. 한도는 Contracts-V2가 정한 값이고, 배포 없이 바꿀 수 있게 설정으로 둔다.
 *
 * 카운터는 이 인스턴스의 메모리에만 있다. 서버가 여러 대가 되면 인스턴스마다 따로 센다.
 */
@Validated
@ConfigurationProperties("gsmsv.rate-limit")
data class RateLimitProperties(
    val enabled: Boolean = true,
    @field:Valid val rules: List<Rule> = emptyList(),
) {
    /**
     * [paths]에 맞는 요청을 [key] 기준으로 [window] 동안 [limit]회까지 받는다.
     * [paths]는 Spring `PathPattern` 문법이다.
     */
    data class Rule(
        @field:NotBlank val name: String,
        @field:NotEmpty val paths: List<String>,
        val key: Key,
        @field:Positive val limit: Int,
        val window: Duration = Duration.ofMinutes(1),
    )

    enum class Key {
        /** 요청을 보낸 IP. 프록시 뒤라면 서버가 전달 헤더를 해석하도록 설정해야 실제 클라이언트 IP가 된다 */
        IP,

        /** 인증된 사용자 id. 인증되지 않은 요청에는 적용하지 않는다 */
        USER,
    }
}
