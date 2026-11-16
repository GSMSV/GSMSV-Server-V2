package com.gsmsv.api.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Go 내부 API 연결 설정. Go 내부 API 클라이언트(네트워크, 이후 VM·지표 등)가 함께 쓴다.
 *
 * [baseUrl]과 [sharedSecret]은 환경 변수로만 받는다. `dev`·`local`은 모의 구현을 쓰므로 비어 있어도 되고,
 * 실제 클라이언트를 만드는 [InfraRestClientConfig]가 비어 있으면 기동을 실패시킨다.
 * [secretHeader]는 공유 시크릿을 실어 보내는 헤더 이름이다 (Go 담당과 정하기 전까지 임시 값).
 */
@ConfigurationProperties("gsmsv.infra")
data class InfraProperties(
    val baseUrl: String = "",
    val sharedSecret: String = "",
    val secretHeader: String = "X-Internal-Secret",
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val readTimeout: Duration = Duration.ofSeconds(10),
) {
    // 시크릿이 로그나 예외 메시지에 찍히지 않게 한다
    override fun toString(): String = "InfraProperties(baseUrl=$baseUrl, secretHeader=$secretHeader)"
}
