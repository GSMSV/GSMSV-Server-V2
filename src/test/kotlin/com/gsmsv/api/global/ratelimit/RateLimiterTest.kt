package com.gsmsv.api.global.ratelimit

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RateLimiterTest {
    private class MutableClock(
        var now: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    ) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    private val clock = MutableClock()
    private val limiter = RateLimiter(clock)
    private val minute = Duration.ofMinutes(1)

    @Test
    fun `한도 안의 요청은 통과시킨다`() {
        repeat(3) { assertNull(limiter.tryAcquire("k", 3, minute)) }
    }

    @Test
    fun `한도를 넘으면 남은 초를 돌려준다`() {
        repeat(3) { limiter.tryAcquire("k", 3, minute) }
        clock.now = clock.now.plusMillis(20_500)

        assertEquals(40L, limiter.tryAcquire("k", 3, minute))
    }

    @Test
    fun `윈도가 지나면 다시 센다`() {
        repeat(3) { limiter.tryAcquire("k", 3, minute) }
        clock.now = clock.now.plusSeconds(60)

        assertNull(limiter.tryAcquire("k", 3, minute))
    }

    @Test
    fun `키마다 따로 센다`() {
        repeat(3) { limiter.tryAcquire("a", 3, minute) }

        assertNull(limiter.tryAcquire("b", 3, minute))
    }
}
