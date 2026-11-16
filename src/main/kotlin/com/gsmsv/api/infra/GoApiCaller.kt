package com.gsmsv.api.infra

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

private val log = KotlinLogging.logger {}

/**
 * Go 내부 API 호출 공통 처리. Go 호출 실패를 공통 오류 형식으로 바꾼다.
 *
 * - 연결 실패·시간 초과, Go의 503·504 → [ErrorCode.UPSTREAM_UNAVAILABLE] (503)
 * - 그 밖의 오류 응답, 읽을 수 없는 응답 → [ErrorCode.GATEWAY_SYNC_FAILED] (502)
 *
 * Go 응답 본문과 주소는 사용자에게 나가는 메시지에 담지 않는다. 로그에는 상태 코드만 남긴다.
 */
class GoApiCaller(
    private val restClient: RestClient,
) {
    fun <T : Any> put(
        operation: String,
        uri: String,
        uriVariables: Map<String, Any>,
        body: Any,
        responseType: Class<T>,
    ): T =
        try {
            restClient
                .put()
                .uri(uri, uriVariables)
                .body(body)
                .retrieve()
                .body(responseType)
                ?: throw syncFailed(operation, "빈 응답")
        } catch (e: BusinessException) {
            throw e
        } catch (e: RestClientResponseException) {
            val status = e.statusCode.value()
            log.warn { "Go 호출 실패: operation=$operation, status=$status" }
            throw if (status == 503 || status == 504) {
                BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE)
            } else {
                BusinessException(ErrorCode.GATEWAY_SYNC_FAILED)
            }
        } catch (e: ResourceAccessException) {
            log.warn { "Go 연결 실패: operation=$operation, cause=${e.cause?.javaClass?.simpleName}" }
            throw BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE)
        } catch (e: RestClientException) {
            throw syncFailed(operation, e.javaClass.simpleName)
        }

    private fun syncFailed(
        operation: String,
        reason: String,
    ): BusinessException {
        log.warn { "Go 응답 처리 실패: operation=$operation, reason=$reason" }
        return BusinessException(ErrorCode.GATEWAY_SYNC_FAILED)
    }
}
