package com.gsmsv.api.global.exception

import org.springframework.http.HttpStatus

// code 이름은 Contracts-V2 공통 에러 code 목록이 정해지면 그에 맞춘다 (Contracts #4)
enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    INVALID_GRN(HttpStatus.BAD_REQUEST, "GRN 형식이 올바르지 않습니다."),
    GRN_RESOURCE_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "이 API에서 다룰 수 없는 자원 종류입니다."),
}
