package com.gsmsv.api.global.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver

/**
 * `Authorization: Bearer` 헤더를 먼저 보고, 없으면 [COOKIE_NAME] 쿠키에서 토큰을 꺼낸다.
 *
 * CSRF 검사를 끄는 대신, 다른 사이트에서 보낸 요청(`Sec-Fetch-Site: cross-site`)에는 쿠키를 쓰지 않는다.
 * 브라우저가 쿠키를 자동으로 붙여도 인증되지 않으므로 다른 사이트가 사용자 몰래 요청을 보낼 수 없다.
 * 헤더는 브라우저가 자동으로 붙이지 않으므로 그대로 받는다.
 */
class AccessTokenResolver : BearerTokenResolver {
    private val headerResolver = DefaultBearerTokenResolver()

    override fun resolve(request: HttpServletRequest): String? = headerResolver.resolve(request) ?: cookieToken(request)

    private fun cookieToken(request: HttpServletRequest): String? {
        if (request.getHeader(FETCH_SITE_HEADER) == CROSS_SITE) return null
        return request.cookies
            ?.firstOrNull { it.name == COOKIE_NAME }
            ?.value
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val COOKIE_NAME = "access_token"
        private const val FETCH_SITE_HEADER = "Sec-Fetch-Site"
        private const val CROSS_SITE = "cross-site"
    }
}
