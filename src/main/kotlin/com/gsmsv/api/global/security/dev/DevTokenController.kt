package com.gsmsv.api.global.security.dev

import com.gsmsv.api.global.grn.IdParser
import com.gsmsv.api.global.security.AccessTokenProvider
import com.gsmsv.api.global.security.Role
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 로그인 없이 원하는 사용자·역할의 액세스 토큰을 발급한다. 로컬·개발 서버에서 프론트와 API를 시험하는 용도다.
 *
 * 운영(prod)에서는 빈 자체를 만들지 않아 경로가 없다. 사용자가 DB에 있는지는 보지 않는다.
 */
@Profile(DevProfiles.DEV, DevProfiles.LOCAL)
@RestController
class DevTokenController(
    private val tokenProvider: AccessTokenProvider,
) {
    @PostMapping(DevProfiles.TOKEN_PATH)
    fun issue(
        @Valid @RequestBody request: DevTokenRequest,
    ): DevTokenResponse {
        val token = tokenProvider.issue(IdParser.requireUserId(request.userId), request.role)
        return DevTokenResponse(token.value, "Bearer", token.expiresIn.seconds)
    }
}

data class DevTokenRequest(
    @field:NotBlank val userId: String,
    val role: Role,
)

data class DevTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
)
