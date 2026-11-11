package com.gsmsv.api.global.security

import com.gsmsv.api.global.config.RequestIdFilter
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.response.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * 보안 필터에서 끝나는 요청(controller까지 가지 않는 요청)도 공통 오류 형식으로 돌려준다.
 *
 * 토큰이 없을 때와 틀렸을 때를 같은 응답으로 돌려준다. 어느 검사에서 실패했는지 밖에 알리지 않는다.
 */
@Component
class SecurityErrorResponder(
    private val jsonMapper: JsonMapper,
) : AuthenticationEntryPoint,
    AccessDeniedHandler {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        write(request, response, ErrorCode.UNAUTHENTICATED)
    }

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) = write(request, response, ErrorCode.ACCESS_DENIED)

    /** 보안 필터 단계에서 controller 없이 오류를 돌려줄 때 쓴다 */
    fun respond(
        request: HttpServletRequest,
        response: HttpServletResponse,
        errorCode: ErrorCode,
    ) = write(request, response, errorCode)

    private fun write(
        request: HttpServletRequest,
        response: HttpServletResponse,
        errorCode: ErrorCode,
    ) {
        val requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE) as? String ?: RequestIdFilter.generate()
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        jsonMapper.writeValue(response.outputStream, ErrorResponse(errorCode.name, errorCode.message, requestId))
    }
}
