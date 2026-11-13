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

/** DataGSM 로그인 시작과 콜백을 처리한다 (Contracts-V2 `openapi/auth.yaml` 1.1~1.2). */
@Service
class LoginService(
    private val dataGsmClient: DataGsmClient,
    private val loginStateService: LoginStateService,
    private val userService: UserService,
    private val properties: AuthProperties,
) {
    /** state를 저장한 뒤 DataGSM 로그인 주소를 돌려준다. */
    fun startLogin(redirect: String?): String = dataGsmClient.authorizationUri(loginStateService.issueState(redirect))

    /** 콜백 성공·실패 주소를 돌려줘 브라우저 흐름에서 JSON 오류가 나가지 않게 한다. */
    fun handleCallback(
        code: String?,
        state: String?,
    ): String =
        try {
            successUrl(login(code, state))
        } catch (e: BusinessException) {
            failureUrl(callbackErrorOf(e.errorCode))
        } catch (e: Exception) {
            // 콜백 예외 메시지에 외부 응답이나 요청 정보가 섞이지 않도록 종류만 기록한다
            log.error { "DataGSM 콜백 처리 실패: cause=${e.javaClass.simpleName}" }
            failureUrl(ErrorCode.OAUTH_EXCHANGE_FAILED)
        }

    private fun login(
        code: String?,
        state: String?,
    ): String {
        val stateValue = state?.takeIf { it.isNotBlank() } ?: throw BusinessException(ErrorCode.OAUTH_STATE_INVALID)
        val loginState = loginStateService.consumeState(stateValue)
        val authorizationCode = code?.takeIf { it.isNotBlank() } ?: throw BusinessException(ErrorCode.OAUTH_EXCHANGE_FAILED)
        val token = dataGsmClient.exchangeCode(authorizationCode)
        val userInfo = dataGsmClient.fetchUserInfo(token.accessToken)
        val email = userInfo.email?.takeIf { it.isNotBlank() } ?: throw BusinessException(ErrorCode.OAUTH_EXCHANGE_FAILED)
        val dataGsmId = userInfo.id.toString()
        val existing = userService.findByDataGsmId(dataGsmId)

        existing?.let(::requireActive)
        // 관리자 여부는 DataGSM 응답이 아니라 이 서버의 기존 계정 role로 판단한다
        if (existing?.role != Role.ADMIN && !userInfo.isEnrolled()) {
            throw BusinessException(ErrorCode.NOT_ENROLLED)
        }

        val user =
            userService.findOrCreateFromDataGsm(
                dataGsmId = dataGsmId,
                email = email,
                name = nameOf(userInfo, email),
                grade = userInfo.student?.grade,
                classNumber = userInfo.student?.classNum,
                studentNumber = userInfo.student?.studentNumber,
            )
        requireActive(user)
        return loginStateService.issueCode(user.id, loginState.redirect)
    }

    private fun requireActive(user: User) {
        if (user.status != UserStatus.ACTIVE) throw BusinessException(ErrorCode.ACCOUNT_INACTIVE)
    }

    private fun nameOf(
        userInfo: DataGsmUserInfo,
        email: String,
    ): String =
        (userInfo.student?.name ?: userInfo.teacher?.name)?.takeIf { it.isNotBlank() }
            ?: email.substringBefore('@')

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
