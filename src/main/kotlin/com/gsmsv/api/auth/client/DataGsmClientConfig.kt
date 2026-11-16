package com.gsmsv.api.auth.client

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

/**
 * [DataGsmClient]를 만든다. [RestClient]를 빈으로 두지 않는 이유는 Go 연동의 `goRestClient`와 타입이 겹치지 않게 하기 위해서다.
 */
@Configuration
class DataGsmClientConfig {
    @Bean
    fun dataGsmClient(properties: DataGsmProperties): DataGsmClient {
        val requestFactory =
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build(),
            ).apply { setReadTimeout(properties.readTimeout) }
        // 기본 설정에는 RestClient.Builder 빈이 없어 직접 만든다
        val base = RestClient.builder().requestFactory(requestFactory)
        return DataGsmClient(
            properties,
            base.clone().baseUrl(properties.authBaseUrl).build(),
            base.clone().baseUrl(properties.resourceBaseUrl).build(),
        )
    }
}
