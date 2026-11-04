package com.gsmsv.api.global.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * 요청마다 `req-` 접두사가 붙은 id를 정해 응답 헤더·오류 응답·로그(MDC)에 같은 값을 쓴다.
 * 보안 필터가 돌려주는 오류에도 헤더가 붙도록 가장 먼저 실행한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader(HEADER)?.takeIf { VALID_REQUEST_ID.matches(it) } ?: generate()
        request.setAttribute(ATTRIBUTE, requestId)
        response.setHeader(HEADER, requestId)
        MDC.put(MDC_KEY, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        const val HEADER = "X-Request-Id"
        const val ATTRIBUTE = "com.gsmsv.api.requestId"
        const val MDC_KEY = "request_id"

        // 클라이언트가 보낸 값은 로그에 그대로 남으므로 글자와 길이를 제한한다
        private val VALID_REQUEST_ID = Regex("^req-[A-Za-z0-9_-]{1,64}$")

        fun generate(): String = "req-" + UUID.randomUUID().toString().replace("-", "")
    }
}
