package com.gsmsv.api.global.ratelimit

import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 고정 윈도 방식 카운터. 윈도가 끝나면 0부터 다시 센다.
 * 윈도 경계에서 한도의 최대 2배가 잠깐 몰릴 수 있지만, 남용을 막는 용도라 단순함을 택했다.
 */
class RateLimiter(
    private val clock: Clock = Clock.systemUTC(),
) {
    private class Window(
        val endsAtMillis: Long,
    ) {
        var count = 0
    }

    private val windows = ConcurrentHashMap<String, Window>()
    private var lastSweepMillis = clock.millis()

    /** 한도 안이면 `null`, 넘었으면 다시 시도할 수 있을 때까지 남은 초(올림, 최소 1)를 돌려준다 */
    fun tryAcquire(
        key: String,
        limit: Int,
        window: Duration,
    ): Long? {
        val now = clock.millis()
        sweep(now)
        var retryAfterMillis: Long? = null
        // compute 안에서 증가시켜 같은 키의 동시 요청도 정확히 센다
        windows.compute(key) { _, current ->
            val active = current?.takeIf { it.endsAtMillis > now } ?: Window(now + window.toMillis())
            if (active.count >= limit) {
                retryAfterMillis = active.endsAtMillis - now
            } else {
                active.count++
            }
            active
        }
        return retryAfterMillis?.let { maxOf(1L, (it + 999) / 1000) }
    }

    // 끝난 윈도가 쌓이지 않게 가끔 지운다
    @Synchronized
    private fun sweep(now: Long) {
        if (now - lastSweepMillis < SWEEP_INTERVAL_MILLIS) return
        lastSweepMillis = now
        windows.entries.removeIf { it.value.endsAtMillis <= now }
    }

    private companion object {
        const val SWEEP_INTERVAL_MILLIS = 60_000L
    }
}
