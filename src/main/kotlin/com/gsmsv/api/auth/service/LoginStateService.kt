package com.gsmsv.api.auth.service

import com.gsmsv.api.auth.client.DataGsmAuthorizationRequest
import com.gsmsv.api.auth.domain.LoginCode
import com.gsmsv.api.auth.domain.LoginState
import com.gsmsv.api.auth.repository.LoginRecordRepository
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64

/**
 * 로그인 state(5분)와 임시 코드(60초)를 발급하고 한 번만 소모하게 한다 (Contracts-V2 `openapi/auth.yaml` 1.1~1.3).
 *
 * 값은 SecureRandom 32바이트(Base64url)이고, DB에는 SHA-256 해시만 저장한다.
 * 소모에 실패하는 경우(없음·이미 씀·만료)를 구분하지 않는다.
 */
@Service
class LoginStateService(
    private val repository: LoginRecordRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val random = SecureRandom()

    /** state를 발급한다. [redirect]는 [sanitizeRedirect]로 정리해 함께 저장한다 */
    @Transactional
    fun issueState(
        redirect: String?,
        codeVerifier: String = "",
    ): String {
        val state = randomValue()
        val now = clock.instant()
        repository.saveState(sha256(state), sanitizeRedirect(redirect), codeVerifier, now, now.plus(STATE_TTL))
        return state
    }

    /** PKCE verifier를 발급한 인가 요청과 함께 state를 저장하고 인가 URI를 돌려준다. */
    @Transactional
    fun createAuthorizationRequest(
        redirect: String?,
        requestFactory: (String) -> DataGsmAuthorizationRequest,
    ): String {
        val state = randomValue()
        val request = requestFactory(state)
        val now = clock.instant()
        repository.saveState(sha256(state), sanitizeRedirect(redirect), request.codeVerifier, now, now.plus(STATE_TTL))
        return request.authorizationUri
    }

    /** state를 소모한다. 없거나 이미 썼거나 만료됐으면 [ErrorCode.OAUTH_STATE_INVALID] */
    @Transactional
    fun consumeState(state: String): LoginState =
        repository.consumeState(sha256(state), clock.instant())
            ?: throw BusinessException(ErrorCode.OAUTH_STATE_INVALID)

    /** [userId]의 임시 코드를 발급한다. [redirect]는 state에서 꺼낸 값을 그대로 넘긴다 */
    @Transactional
    fun issueCode(
        userId: String,
        redirect: String,
    ): String {
        val code = CODE_PREFIX + randomValue()
        val now = clock.instant()
        repository.saveCode(sha256(code), userId, sanitizeRedirect(redirect), now, now.plus(CODE_TTL))
        return code
    }

    /** 임시 코드를 소모한다. 없거나 이미 썼거나 만료됐으면 [ErrorCode.GONE] */
    @Transactional
    fun consumeCode(code: String): LoginCode =
        repository.consumeCode(sha256(code), clock.instant())
            ?: throw BusinessException(ErrorCode.GONE)

    /** 만료된 행을 지우고 지운 개수를 돌려준다 */
    @Transactional
    fun deleteExpired(): Int {
        val (states, codes) = repository.deleteExpired(clock.instant())
        return states + codes
    }

    private fun randomValue(): String {
        val bytes = ByteArray(VALUE_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        val STATE_TTL: Duration = Duration.ofMinutes(5)
        val CODE_TTL: Duration = Duration.ofSeconds(60)
        private const val VALUE_BYTES = 32
        private const val CODE_PREFIX = "tmp_"
        private const val DEFAULT_REDIRECT = "/"
        private const val MAX_REDIRECT_LENGTH = 512
        private val ENCODED_PATH_SEPARATOR = Regex("(?i)%2f|%5c")

        /**
         * 로그인 뒤 돌아갈 경로를 정리한다. 열린 리다이렉트를 막으려고 `/`로 시작하고 `//`·`/\`로 시작하지 않는 상대 경로만 받는다.
         * 규칙에 맞지 않거나 없으면 `/`다. 제어 문자·역슬래시가 있거나 너무 길어도 `/`로 본다.
         */
        fun sanitizeRedirect(redirect: String?): String {
            if (redirect == null || redirect.length > MAX_REDIRECT_LENGTH) return DEFAULT_REDIRECT
            val valid =
                redirect.startsWith("/") &&
                    !redirect.startsWith("//") &&
                    !redirect.startsWith("/\\") &&
                    !redirect.contains('#') &&
                    !ENCODED_PATH_SEPARATOR.containsMatchIn(redirect) &&
                    redirect.none { it == '\\' || it.isISOControl() }
            return if (valid) redirect else DEFAULT_REDIRECT
        }

        fun sha256(value: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
