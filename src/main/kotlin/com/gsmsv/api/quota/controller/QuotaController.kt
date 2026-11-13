package com.gsmsv.api.quota.controller

import com.gsmsv.api.global.authorization.Authenticated
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.quota.dto.MyQuotaResponse
import com.gsmsv.api.quota.service.MyQuotaService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 내 한도와 사용량 API (4.2). 본인 정보만 다루므로 `@Authenticated`로 받고 요청자 id로만 센다 */
@RestController
@RequestMapping("/api/v2/users/me/quota")
class QuotaController(
    private val myQuotaService: MyQuotaService,
) {
    @Authenticated
    @GetMapping
    fun get(
        @CurrentUser user: AuthenticatedUser,
    ): MyQuotaResponse = myQuotaService.get(user)
}
