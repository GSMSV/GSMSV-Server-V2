package com.gsmsv.api.auth.client

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private val log = KotlinLogging.logger {}

/**
 * DataGSM OAuth 클라이언트 (https://docs.datagsm.kr/oauth).
 *
 * 실패는 두 가지로 나눠 던진다.
 * - 인가 코드·토큰이 거절됐다(4xx) → [ErrorCode.OAUTH_EXCHANGE_FAILED]
 * - DataGSM이 응답하지 않는다(5xx·타임아웃·연결 실패·깨진 응답) → [ErrorCode.UPSTREAM_UNAVAILABLE]
 * - 요청 한도 초과(429) → [ErrorCode.RATE_LIMITED]
 *
 * 인가 코드·토큰·secret은 로그와 예외 메시지에 남기지 않는다. 상태 코드만 남긴다.
 */
class DataGsmClient(
    private val properties: DataGsmProperties,
    private val authClient: RestClient,
    private val resourceClient: RestClient,
) {
    // 앱 전체 Jackson 설정(snake_case, 모르는 필드 거부)은 DataGSM 응답과 맞지 않아 전용 매퍼로 읽는다
    private val mapper: JsonMapper = JsonMapper.builder().findAndAddModules().build()

    /** DataGSM 로그인 화면 주소. [state]는 콜백에서 그대로 돌아온다 */
    fun authorizationUri(state: String): String = authorizationUri(state, null)

    /** PKCE 인가 요청을 만들고 콜백까지 [state]와 검증값을 함께 보관한다. */
    fun authorizationRequest(state: String): DataGsmAuthorizationRequest {
        val verifierBytes = ByteArray(PKCE_VERIFIER_BYTES).also(secureRandom::nextBytes)
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes)
        val challenge =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        return DataGsmAuthorizationRequest(authorizationUri(state, challenge), verifier)
    }

    private fun authorizationUri(
        state: String,
        codeChallenge: String?,
    ): String {
        val uriVariables =
            mutableMapOf<String, Any>(
                "clientId" to properties.clientId,
                "redirectUri" to properties.redirectUri,
                "state" to state,
            )
        val builder =
            UriComponentsBuilder
                .fromUriString(properties.authBaseUrl)
                .path("/v1/oauth/authorize")
                .queryParam("client_id", "{clientId}")
                .queryParam("redirect_uri", "{redirectUri}")
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("state", "{state}")
                .apply {
                    if (codeChallenge != null) {
                        queryParam("code_challenge", "{codeChallenge}")
                        queryParam("code_challenge_method", "S256")
                        uriVariables["codeChallenge"] = codeChallenge
                    }
                }
        return builder.encode().buildAndExpand(uriVariables).toUriString()
    }

    /** 인가 코드를 DataGSM 토큰으로 바꾼다 */
    fun exchangeCode(code: String): DataGsmToken = exchangeCodeInternal(code, null)

    /** [authorizationRequest]에서 만든 검증값과 인가 코드를 DataGSM 토큰으로 교환한다. */
    fun exchangeCode(
        code: String,
        codeVerifier: String,
    ): DataGsmToken = exchangeCodeInternal(code, codeVerifier)

    private fun exchangeCodeInternal(
        code: String,
        codeVerifier: String?,
    ): DataGsmToken {
        val form =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("code", code)
                add("client_id", properties.clientId)
                add("client_secret", properties.clientSecret)
                add("redirect_uri", properties.redirectUri)
                codeVerifier?.let { add("code_verifier", it) }
            }
        return call("token-exchange", DataGsmToken::class.java) {
            authClient
                .post()
                .uri("/v1/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String::class.java)
        }
    }

    /** 사용자 정보를 조회한다. 재학 여부는 [DataGsmUserInfo.isEnrolled]로 본다 */
    fun fetchUserInfo(accessToken: String): DataGsmUserInfo =
        call("userinfo", DataGsmUserInfo::class.java) {
            resourceClient
                .get()
                .uri("/userinfo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .body(String::class.java)
        }

    private fun <T : Any> call(
        operation: String,
        type: Class<T>,
        request: () -> String?,
    ): T {
        try {
            val body = request() ?: throw unavailable(operation, "빈 응답")
            return mapper.readValue(body, type)
        } catch (e: RestClientResponseException) {
            val status = e.statusCode.value()
            log.warn { "DataGSM 호출 실패: operation=$operation, status=$status" }
            throw when {
                status == 429 -> BusinessException(ErrorCode.RATE_LIMITED)
                status >= 500 -> BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, message = UNAVAILABLE_MESSAGE)
                else -> BusinessException(ErrorCode.OAUTH_EXCHANGE_FAILED)
            }
        } catch (e: RestClientException) {
            // 타임아웃·연결 실패. 예외 메시지에 주소가 들어갈 수 있어 종류만 남긴다
            log.warn { "DataGSM 호출 실패: operation=$operation, cause=${e.javaClass.simpleName}" }
            throw BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, message = UNAVAILABLE_MESSAGE)
        } catch (e: JacksonException) {
            throw unavailable(operation, "응답 형식 오류")
        }
    }

    private fun unavailable(
        operation: String,
        reason: String,
    ): BusinessException {
        log.warn { "DataGSM 호출 실패: operation=$operation, reason=$reason" }
        return BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, message = UNAVAILABLE_MESSAGE)
    }

    companion object {
        // 계정 기본 정보(이메일 포함)와 학생 정보(재학 판단용)
        const val SCOPE = "datagsm:account_read datagsm:student_read"
        private const val UNAVAILABLE_MESSAGE = "학생 정보 서비스가 응답하지 않습니다."
        private const val PKCE_VERIFIER_BYTES = 32
        private val secureRandom = SecureRandom()
    }
}
