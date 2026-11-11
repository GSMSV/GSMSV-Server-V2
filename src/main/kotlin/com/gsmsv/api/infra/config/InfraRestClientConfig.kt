package com.gsmsv.api.infra.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

/** 모의 구현을 쓰는 프로필. 이 프로필에서는 Go 연결 설정이 없어도 된다 */
object InfraProfiles {
    const val MOCK = "local | dev"
    const val REAL = "!local & !dev"
}

/** Go 내부 API 공통 HTTP 설정. 모든 Go 클라이언트가 이 [RestClient]를 받아 쓴다 */
@Configuration
@Profile(InfraProfiles.REAL)
class InfraRestClientConfig {
    @Bean
    fun goRestClient(
        builder: RestClient.Builder,
        properties: InfraProperties,
    ): RestClient {
        require(properties.baseUrl.isNotBlank()) { "gsmsv.infra.base-url(INFRA_BASE_URL)이 비어 있습니다." }
        require(properties.sharedSecret.isNotBlank()) { "gsmsv.infra.shared-secret(INFRA_SHARED_SECRET)이 비어 있습니다." }

        val requestFactory =
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build(),
            ).apply { setReadTimeout(properties.readTimeout) }

        return builder
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .defaultHeader(properties.secretHeader, properties.sharedSecret)
            .build()
    }
}
