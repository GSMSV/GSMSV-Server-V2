package com.gsmsv.api.global.security

/** 계정의 역할 (Contracts-V2 `policies/feature-policies.md` 1번). 팀 안의 역할은 [com.gsmsv.api.global.authorization.TeamRole]이다 */
enum class Role {
    USER,
    ADMIN,
}
