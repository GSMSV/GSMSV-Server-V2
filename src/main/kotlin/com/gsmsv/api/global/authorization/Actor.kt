package com.gsmsv.api.global.authorization

/**
 * 권한 판단을 받는 주체.
 *
 * HTTP 요청에서는 [RequestActor]만 만들 수 있다. controller·인증 필터는 [RequestActor] 타입으로만 주체를 받는다.
 * [SystemActor]는 [RequestActor]가 아니므로 요청 경로로 흘러 들어갈 수 없고, 내부 작업 코드에서만 쓴다.
 */
sealed interface Actor

/** HTTP 요청으로 인증된 주체. */
sealed interface RequestActor : Actor {
    val userId: String
}

/** 일반 사용자. 개인 자원 소유와 팀 역할로 판단한다. */
data class UserActor(
    override val userId: String,
) : RequestActor

/** 관리자. "모든 동작 허용" 규칙으로 통과하고, 통과할 때마다 감사 로그를 남긴다. */
data class AdminActor(
    override val userId: String,
) : RequestActor

/** 스케줄러 같은 내부 작업 전용 주체. 요청 처리 코드에서 쓰지 않는다. */
data object SystemActor : Actor
