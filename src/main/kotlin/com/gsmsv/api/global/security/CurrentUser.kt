package com.gsmsv.api.global.security

/**
 * controller 메서드의 [AuthenticatedUser] 인자에 붙이면 인증된 요청자를 넣어 준다.
 *
 * 인증이 필요한 경로는 보안 필터가 먼저 401로 막으므로, 이 인자가 채워지지 않은 채 메서드가 실행되는 일은 없다.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentUser
