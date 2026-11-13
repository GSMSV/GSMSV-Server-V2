package com.gsmsv.api.global.exception

import org.springframework.http.HttpStatus

// 공통 code 목록은 Contracts-V2가 아직 정하지 않았다 (api-rules 3장). 정해지면 이름을 맞춘다 (#18)
enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    LOCKED_CATEGORY(HttpStatus.BAD_REQUEST, "끌 수 없는 필수 알림입니다."),
    UNKNOWN_FIELD(HttpStatus.BAD_REQUEST, "알 수 없는 필드가 있습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "커서 형식이 올바르지 않습니다."),
    INVALID_GRN(HttpStatus.BAD_REQUEST, "GRN 형식이 올바르지 않습니다."),
    GRN_RESOURCE_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "이 API에서 다룰 수 없는 자원 종류입니다."),
    INVALID_RESOURCE_ID(HttpStatus.BAD_REQUEST, "id 형식이 올바르지 않습니다."),
    OAUTH_STATE_INVALID(HttpStatus.BAD_REQUEST, "로그인 요청을 확인하지 못했습니다. 다시 시도해 주세요."),
    CONFIRMATION_MISMATCH(HttpStatus.BAD_REQUEST, "확인 값이 대상과 다릅니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 동작을 할 권한이 없습니다."),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 팀원입니다."),
    CONFLICT(HttpStatus.CONFLICT, "요청이 현재 상태와 충돌합니다."),
    TEAM_NOT_APPROVED(HttpStatus.FORBIDDEN, "승인되지 않은 팀은 이 동작을 할 수 없습니다."),
    INVITATION_EMAIL_MISMATCH(HttpStatus.FORBIDDEN, "초대 대상 이메일과 로그인한 계정이 다릅니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "자원을 찾을 수 없습니다."),
    GONE(HttpStatus.GONE, "초대가 만료되었거나 더 이상 유효하지 않습니다."),
    QUOTA_EXCEEDED(HttpStatus.CONFLICT, "앱 개수 한도를 초과했습니다."),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 API가 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "이 API에서 지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    GATEWAY_SYNC_FAILED(HttpStatus.BAD_GATEWAY, "게이트웨이 반영에 실패했습니다."),
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "가상화 서버가 응답하지 않습니다."),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
}
