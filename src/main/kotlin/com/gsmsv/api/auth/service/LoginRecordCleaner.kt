package com.gsmsv.api.auth.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/** 스케줄링을 켠다. 소모 조건에 만료 시각이 있어 정리가 늦어도 만료된 값은 쓸 수 없다 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class AuthSchedulingConfig

/**
 * 만료된 state·임시 코드 행을 주기적으로 지운다.
 * 지우기는 멱등이라 인스턴스가 여러 개여도 같이 돌려도 된다.
 */
@Component
class LoginRecordCleaner(
    private val service: LoginStateService,
) {
    @Scheduled(fixedDelayString = "\${gsmsv.auth.login-cleanup-interval:PT1M}")
    fun cleanUp() {
        val deleted = service.deleteExpired()
        if (deleted > 0) log.info { "만료된 로그인 state·임시 코드를 지웠습니다. 개수=$deleted" }
    }
}
