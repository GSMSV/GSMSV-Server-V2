package com.gsmsv.api.global.security

import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class AuthCookieWriter(
    private val properties: AuthCookieProperties,
) {
    fun write(
        headers: HttpHeaders,
        accessToken: String,
        accessTokenTtl: Duration,
        refreshToken: String,
    ) {
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, accessToken, "/", accessTokenTtl).toString())
        headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, refreshToken, REFRESH_PATH, RefreshTokenTtl).toString())
    }

    fun clear(headers: HttpHeaders) {
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, "", "/", Duration.ZERO).toString())
        headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO).toString())
    }

    private fun cookie(
        name: String,
        value: String,
        path: String,
        maxAge: Duration,
    ): ResponseCookie =
        ResponseCookie
            .from(name, value)
            .httpOnly(true)
            .secure(properties.secure)
            .sameSite("Lax")
            .path(path)
            .maxAge(maxAge)
            .build()

    companion object {
        const val ACCESS_COOKIE = "access_token"
        const val REFRESH_COOKIE = "refresh_token"
        const val REFRESH_PATH = "/api/v2/auth"
        val RefreshTokenTtl: Duration = Duration.ofDays(7)
    }
}
