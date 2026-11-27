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
 * - Go의 409(같은 자원에 끝나지 않은 작업이 있다 등) → [ErrorCode.CONFLICT] (409)
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
        call(operation, notFoundAsNull = false) {
            restClient
                .put()
                .uri(uri, uriVariables)
                .body(body)
                .retrieve()
                .body(responseType)
        } ?: throw syncFailed(operation, "빈 응답")

    /** 명령형 작업 요청. [headers]에는 `Idempotency-Key` 같은 요청별 헤더를 넣는다 */
    fun <T : Any> post(
        operation: String,
        uri: String,
        uriVariables: Map<String, Any>,
        headers: Map<String, String>,
        body: Any,
        responseType: Class<T>,
    ): T =
        call(operation, notFoundAsNull = false) {
            restClient
                .post()
                .uri(uri, uriVariables)
                .headers { h -> headers.forEach { (name, value) -> h.set(name, value) } }
                .body(body)
                .retrieve()
                .body(responseType)
        } ?: throw syncFailed(operation, "빈 응답")

    /** 지우기 요청. Go가 404로 답하면 null이다 (지울 대상이 이미 없다) */
    fun <T : Any> deleteOrNull(
        operation: String,
        uri: String,
        uriVariables: Map<String, Any>,
        headers: Map<String, String>,
        responseType: Class<T>,
    ): T? =
        call(operation, notFoundAsNull = true) {
            restClient
                .delete()
                .uri(uri, uriVariables)
                .headers { h -> headers.forEach { (name, value) -> h.set(name, value) } }
                .retrieve()
                .body(responseType)
                ?: throw syncFailed(operation, "빈 응답")
        }

    /** 조회. Go가 404로 답하면 null이다 (예: 작업 기록이 사라진 `JOB_NOT_FOUND`) */
    fun <T : Any> getOrNull(
        operation: String,
        uri: String,
        uriVariables: Map<String, Any>,
        responseType: Class<T>,
    ): T? =
        call(operation, notFoundAsNull = true) {
            restClient
                .get()
                .uri(uri, uriVariables)
                .retrieve()
                .body(responseType)
                ?: throw syncFailed(operation, "빈 응답")
        }

    /** 조회. 404도 다른 오류와 같이 실패로 바꾼다 */
    fun <T : Any> get(
        operation: String,
        uri: String,
        uriVariables: Map<String, Any>,
        responseType: Class<T>,
    ): T =
        call(operation, notFoundAsNull = false) {
            restClient
                .get()
                .uri(uri, uriVariables)
                .retrieve()
                .body(responseType)
        } ?: throw syncFailed(operation, "빈 응답")

    private fun <T : Any> call(
        operation: String,
        notFoundAsNull: Boolean,
        request: () -> T?,
    ): T? =
        try {
            request()
        } catch (e: BusinessException) {
            throw e
        } catch (e: RestClientResponseException) {
            if (notFoundAsNull && e.statusCode.value() == 404) null else throw translate(operation, e)
        } catch (e: ResourceAccessException) {
            log.warn { "Go 연결 실패: operation=$operation, cause=${e.cause?.javaClass?.simpleName}" }
            throw BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE)
        } catch (e: RestClientException) {
            throw syncFailed(operation, e.javaClass.simpleName)
        }

    private fun translate(
        operation: String,
        e: RestClientResponseException,
    ): BusinessException {
        val status = e.statusCode.value()
        log.warn { "Go 호출 실패: operation=$operation, status=$status" }
        return when (status) {
            409 -> BusinessException(ErrorCode.CONFLICT)
            503, 504 -> BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE)
            else -> BusinessException(ErrorCode.GATEWAY_SYNC_FAILED)
        }
    }

    private fun syncFailed(
        operation: String,
        reason: String,
    ): BusinessException {
        log.warn { "Go 응답 처리 실패: operation=$operation, reason=$reason" }
        return BusinessException(ErrorCode.GATEWAY_SYNC_FAILED)
    }
}
