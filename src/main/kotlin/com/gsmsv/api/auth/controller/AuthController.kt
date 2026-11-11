package com.gsmsv.api.auth.controller

import com.gsmsv.api.auth.service.LoginService
import com.gsmsv.api.global.authorization.Public
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 로그인 API (Contracts-V2 `openapi/auth.yaml` 1.1~1.2). 두 경로 모두 인증 없이 부르고 302로 응답한다 */
@RestController
@RequestMapping("/api/v2/auth/datagsm")
class AuthController(
    private val loginService: LoginService,
) {
    /** 1.1 DataGSM 로그인 시작 */
    @Public
    @GetMapping("/authorize")
    fun authorize(
        @RequestParam(required = false) redirect: String?,
    ): ResponseEntity<Void> = redirectTo(loginService.startLogin(redirect))

    /**
     * 1.2 DataGSM 콜백. 실패도 JSON이 아니라 프론트 `/login?error=`로 302 한다.
     * 파라미터가 빠져도 400 JSON이 아니라 같은 방식으로 실패 처리하려고 모두 선택으로 받는다.
     */
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
            // 코드·state가 담긴 주소가 캐시나 중간 서버에 남지 않게 한다
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .build()
}
