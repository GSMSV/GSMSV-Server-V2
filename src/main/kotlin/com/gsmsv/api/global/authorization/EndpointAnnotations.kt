package com.gsmsv.api.global.authorization

/**
 * 이 엔드포인트가 [action] 권한을 판단받는 동작임을 선언한다 (22번 5.1절).
 *
 * 실제 판단은 로더가 한다. 로더에 넘긴 동작이 여기 선언한 동작과 다르면 요청을 실패시킨다.
 *
 * 계약 예시는 문자열(`"vm.delete"`)이지만 동작 이름 형식이 아직 정해지지 않아 [Action]으로 받는다 (22번 13장).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class RequirePermission(
    val action: Action,
)

/** 로그인만 하면 부를 수 있는 엔드포인트 (예: 내 정보, 목록 조회). 자원 로더를 부르지 않는다. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Authenticated

/**
 * 인증 없이 부를 수 있는 엔드포인트. 경로를 `SecurityConfig.PUBLIC_PATHS`에도 추가해야 실제로 열린다.
 * 둘 중 하나만 있으면 401로 닫힌 쪽이 된다.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Public

internal val ENDPOINT_ANNOTATIONS = listOf(RequirePermission::class, Authenticated::class, Public::class)
