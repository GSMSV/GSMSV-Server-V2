package com.gsmsv.api.global.ratelimit

import com.gsmsv.api.global.config.RequestIdFilter
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.response.ErrorResponse
import com.gsmsv.api.global.security.AuthenticatedUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.server.PathContainer
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.pattern.PathPattern
import org.springframework.web.util.pattern.PathPatternParser
import tools.jackson.databind.json.JsonMapper

/**
 * 설정한 규칙에 맞는 요청이 한도를 넘으면 429 `RATE_LIMITED`와 `Retry-After`로 끊는다.
 *
 * `USER` 키는 인증 결과가 필요해서 보안 필터 체인 안(토큰 검증 뒤)에 넣는다. 서블릿 필터로 따로 등록하지 않는다.
 */
class RateLimitFilter(
    properties: RateLimitProperties,
    private val limiter: RateLimiter,
    private val jsonMapper: JsonMapper,
) : OncePerRequestFilter() {
    private val enabled = properties.enabled
    private val rules =
        properties.rules.map { rule ->
            CompiledRule(rule, rule.paths.map { PathPatternParser.defaultInstance.parse(it) })
        }

    private class CompiledRule(
        val rule: RateLimitProperties.Rule,
        val patterns: List<PathPattern>,
    )

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (enabled) {
            val path = PathContainer.parsePath(request.requestURI)
            for (compiled in rules) {
                if (compiled.patterns.none { it.matches(path) }) continue
                val subject = subjectOf(compiled.rule.key, request) ?: continue
                val retryAfter = limiter.tryAcquire("${compiled.rule.name}:$subject", compiled.rule.limit, compiled.rule.window)
                if (retryAfter != null) {
                    reject(request, response, retryAfter)
                    return
                }
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun subjectOf(
        key: RateLimitProperties.Key,
        request: HttpServletRequest,
    ): String? =
        when (key) {
            RateLimitProperties.Key.IP -> request.remoteAddr
            RateLimitProperties.Key.USER -> (SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser)?.userId
        }

    private fun reject(
        request: HttpServletRequest,
        response: HttpServletResponse,
        retryAfterSeconds: Long,
    ) {
        val requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE) as? String ?: RequestIdFilter.generate()
        response.status = ErrorCode.RATE_LIMITED.status.value()
        response.setHeader(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        jsonMapper.writeValue(
            response.outputStream,
            ErrorResponse(ErrorCode.RATE_LIMITED.name, ErrorCode.RATE_LIMITED.message, requestId),
        )
    }
}
