package com.gsmsv.api.auth.service

import com.gsmsv.api.auth.client.DataGsmClient
import com.gsmsv.api.auth.client.DataGsmUserInfo
import com.gsmsv.api.auth.config.AuthProperties
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.domain.UserStatus
import com.gsmsv.api.user.service.UserService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder

private val log = KotlinLogging.logger {}

/**
 * DataGSM 로그인 시작(1.1)과 콜백(1.2)을 처리한다 (Contracts-V2 `openapi/auth.yaml`).
 *
 * 콜백은 브라우저 리다이렉트 흐름이라 실패도 JSON이 아니라 프론트 `/login?error={code}`로 보낸다.
 * 그래서 [handleCallback]은 예외를 던지지 않고 이동할 주소를 돌려준다.
 */
@Service
class LoginService(
    private val dataGsmClient: DataGsmClient,
    private val loginStateService: LoginStateService,
    private val userService: UserService,
    private val properties: AuthProperties,
) {
    /** 1.1: state를 만들어 5분간 보관하고 DataGSM 로그인 화면 주소를 돌려준다 */
    fun startLogin(redirect: String?): String = dataGsmClient.authorizationUri(loginStateService.issueState(redirect))

    /** 1.2: 콜백을 처리하고 브라우저를 보낼 프론트 주소(성공 또는 실패)를 돌려준다 */
    fun handleCallback(
        code: String?,
        state: String?,
    ): String =
        try {
            successUrl(login(code, state))
        } catch (e: BusinessException) {
            failureUrl(callbackErrorOf(e.errorCode))
        } catch (e: Exception) {
            // 예상하지 못한 오류도 JSON이 아니라 프론트로 보낸다. 원인은 로그에만 남긴다
            log.error(e) { "DataGSM 콜백 처리 중 예상하지 못한 오류" }
            failureUrl(ErrorCode.OAUTH_EXCHANGE_FAILED)
        }

    private fun login(
        code: String?,
        state: String?,
    ): String {
        // state를 가장 먼저 소모한다. 없거나 틀리면 DataGSM을 부르지 않는다
        val loginState =
            loginStateService.consumeState(state?.takeIf { it.isNotBlank() } ?: throw BusinessException(ErrorCode.OAUTH_STATE_INVALID))
        // 사용자가 DataGSM에서 거절하면 code 없이 돌아온다
        val authorizationCode = code?.takeIf { it.isNotBlank() } ?: throw BusinessException(ErrorCode.OAUTH_EXCHANGE_FAILED)

        val token = dataGsmClient.exchangeCode(authorizationCode)
        val userInfo = dataGsmClient.fetchUserInfo(token.accessToken)
        val email = userInfo.email?.takeIf { it.isNotBlank() } ?: throw BusinessException(ErrorCode.OAUTH_EXCHANGE_FAILED)
        val dataGsmId = userInfo.id.toString()

        val existing = userService.findByDataGsmId(dataGsmId)
        if (existing != null) requireActive(existing)
        // 관리자는 졸업해도 계속 쓸 수 있어 재학 여부를 확인하지 않는다. 관리자 여부는 DataGSM role이 아니라 이 서비스 계정의 role이다
        if (existing?.role != Role.ADMIN && !userInfo.isEnrolled()) throw BusinessException(ErrorCode.NOT_ENROLLED)

        // 학교 정보는 로그인할 때마다 DataGSM 값으로 덮어쓴다
        val student = userInfo.student
        val user =
            userService.findOrCreateFromDataGsm(
                dataGsmId = dataGsmId,
                email = email,
                name = nameOf(userInfo, email),
                grade = student?.grade,
                classNumber = student?.classNum,
                studentNumber = student?.studentNumber,
            )
        requireActive(user)
        return loginStateService.issueCode(user.id, loginState.redirect)
    }

    private fun requireActive(user: User) {
        if (user.status != UserStatus.ACTIVE) throw BusinessException(ErrorCode.ACCOUNT_INACTIVE)
    }

    // 학생·교사 정보가 없는 계정(관리자 등)은 이메일 앞부분을 이름으로 쓴다
    private fun nameOf(
        userInfo: DataGsmUserInfo,
        email: String,
    ): String =
        (userInfo.student?.name ?: userInfo.teacher?.name)?.takeIf { it.isNotBlank() }
            ?: email.substringBefore('@')

    // 계약의 CallbackErrorCode에 있는 값만 프론트에 내보낸다
    private fun callbackErrorOf(errorCode: ErrorCode): ErrorCode =
        if (errorCode in CALLBACK_ERRORS) errorCode else ErrorCode.OAUTH_EXCHANGE_FAILED

    private fun successUrl(temporaryCode: String): String =
        UriComponentsBuilder
            .fromUriString(properties.frontendUrl)
            .path("/login/callback")
            .queryParam("code", temporaryCode)
            .encode()
            .build()
            .toUriString()

    private fun failureUrl(error: ErrorCode): String =
        UriComponentsBuilder
            .fromUriString(properties.frontendUrl)
            .path("/login")
            .queryParam("error", error.name)
            .encode()
            .build()
            .toUriString()

    private companion object {
        val CALLBACK_ERRORS =
            setOf(
                ErrorCode.OAUTH_STATE_INVALID,
                ErrorCode.OAUTH_EXCHANGE_FAILED,
                ErrorCode.NOT_ENROLLED,
                ErrorCode.UPSTREAM_UNAVAILABLE,
                ErrorCode.ACCOUNT_INACTIVE,
            )
    }
}
