package com.gsmsv.api.auth.controller

import com.gsmsv.api.auth.service.TokenService
import com.gsmsv.api.global.authorization.Public
import com.gsmsv.api.global.security.AuthCookieWriter
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
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

@RestController
@RequestMapping("/api/v2/auth")
class AuthController(
    private val tokenService: TokenService,
    private val cookieWriter: AuthCookieWriter,
) {
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
                    LoginUserResponse(
                        tokens.user.id,
                        tokens.user.name,
                        tokens.user.role.name
                            .lowercase(),
                    ),
                    tokens.redirect,
                ),
            )
    }
}
