package com.gsmsv.api.vm.dto

/** 4.7 VM 초기 비밀번호. 비밀번호가 들어 있으므로 응답에 `Cache-Control: no-store`를 붙인다 */
data class VmCredentialResponse(
    val user: String,
    val initialPassword: String,
    val notice: String,
) {
    // 비밀번호가 로그·예외 메시지에 찍히지 않게 data class 기본 toString을 덮는다
    override fun toString(): String = "VmCredentialResponse(user=$user)"
}
