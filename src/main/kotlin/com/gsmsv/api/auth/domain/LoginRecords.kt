package com.gsmsv.api.auth.domain

/** 소모한 로그인 state. [redirect]는 1.1에서 받아 정리한 프론트 경로다 */
data class LoginState(
    val redirect: String,
)

/** 소모한 임시 코드. [userId]의 로그인이고, [redirect]는 1.3 응답으로 돌려준다 */
data class LoginCode(
    val userId: String,
    val redirect: String,
)
