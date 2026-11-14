package com.gsmsv.api.auth.controller

import com.gsmsv.api.auth.service.LoginService
import com.gsmsv.api.auth.service.TokenService
import com.gsmsv.api.global.authorization.Public
import com.gsmsv.api.global.security.AuthCookieWriter
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class TokenExchangeRequest(
    @field:NotBlank
    val code: String,
)

data class TokenExchangeResponse(
    val user: LoginUserResponse,
    val redirect: String,
)

data class LoginUserResponse(
    val id: String,
    val name: String,
    val role: String,
)

/** Auth 로그인 시작·콜백·토큰 교환 API를 처리한다. */
@RestController
@RequestMapping("/api/v2/auth")
class AuthController(
    private val tokenService: TokenService,
    private val cookieWriter: AuthCookieWriter,
    private val loginService: LoginService,
) {
    @Public
    @GetMapping("/datagsm/authorize")
    fun authorize(
        @RequestParam(required = false) redirect: String?,
    ): ResponseEntity<Void> = redirectTo(loginService.startLogin(redirect))

    @Public
    @GetMapping("/datagsm/callback")
    fun callback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
    ): ResponseEntity<Void> = redirectTo(loginService.handleCallback(code, state))

    @Public
    @PostMapping("/token-exchange")
    fun exchange(
        @Valid @RequestBody request: TokenExchangeRequest,
    ): ResponseEntity<TokenExchangeResponse> {
        val tokens = tokenService.exchange(request.code)
        val headers = HttpHeaders()
        cookieWriter.write(headers, tokens.accessToken.value, tokens.accessToken.expiresIn, tokens.refreshToken.value)
        return ResponseEntity
            .ok()
            .headers(headers)
            .body(
                TokenExchangeResponse(
                    LoginUserResponse(tokens.user.id, tokens.user.name, tokens.user.role.name.lowercase()),
                    tokens.redirect,
                ),
            )
    }

    private fun redirectTo(location: String): ResponseEntity<Void> =
        ResponseEntity
            .status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, location)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .build()
}
