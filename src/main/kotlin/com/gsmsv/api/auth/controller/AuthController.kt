package com.gsmsv.api.auth.controller

import com.gsmsv.api.auth.service.LoginService
import com.gsmsv.api.auth.service.LoginStateService
import com.gsmsv.api.global.authorization.Public
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 로그인 시작과 DataGSM 콜백을 처리한다 (Contracts-V2 `openapi/auth.yaml` 1.1~1.2). */
@RestController
@RequestMapping("/api/v2/auth/datagsm")
class AuthController(
    private val loginService: LoginService,
) {
    @Public
    @GetMapping("/authorize")
    fun authorize(
        @RequestParam(required = false) redirect: String?,
    ): ResponseEntity<Void> = redirectTo(loginService.startLogin(LoginStateService.sanitizeRedirect(redirect)))

    @Public
    @GetMapping("/callback")
    fun callback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
    ): ResponseEntity<Void> = redirectTo(loginService.handleCallback(code, state))

    private fun redirectTo(location: String): ResponseEntity<Void> =
        ResponseEntity
            .status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, location)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .build()
}
