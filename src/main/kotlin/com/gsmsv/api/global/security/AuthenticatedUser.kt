package com.gsmsv.api.global.security

import com.gsmsv.api.global.authorization.AdminActor
import com.gsmsv.api.global.authorization.RequestActor
import com.gsmsv.api.global.authorization.UserActor

/** 검증된 토큰에서 꺼낸 요청자. controller는 [CurrentUser]로 받고, 권한 판단에는 [toActor]로 바꿔 넘긴다 */
data class AuthenticatedUser(
    val userId: String,
    val role: Role,
) {
    fun toActor(): RequestActor =
        when (role) {
            Role.USER -> UserActor(userId)
            Role.ADMIN -> AdminActor(userId)
        }
}
