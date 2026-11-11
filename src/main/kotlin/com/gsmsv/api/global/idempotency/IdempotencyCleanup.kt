package com.gsmsv.api.global.idempotency

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class IdempotencyConfig

/** 만료된 멱등성 키를 정리한다. 만료된 키는 조회할 때도 새 키로 취급하므로 정리가 늦어져도 동작은 같다 */
@Component
class IdempotencyCleanup(
    private val store: IdempotencyStore,
) {
    @Scheduled(cron = "0 17 * * * *")
    fun deleteExpired() {
        store.deleteExpired(Instant.now())
    }
}
