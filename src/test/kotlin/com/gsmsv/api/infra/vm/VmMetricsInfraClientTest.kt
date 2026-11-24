package com.gsmsv.api.infra.vm

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VmMetricsInfraClientTest {
    private val now = Instant.parse("2026-11-24T09:00:45Z")
    private val fake = FakeVmMetricsInfraClient(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `모의 구현은 hour면 1분 간격 60개, day면 30분 간격 48개를 오래된 것부터 준다`() {
        val hour = fake.getMetrics(VM_ID, VmMetricsPeriod.HOUR)
        val day = fake.getMetrics(VM_ID, VmMetricsPeriod.DAY)

        assertEquals(60, hour.size)
        assertEquals(Instant.parse("2026-11-24T09:00:00Z"), hour.last().at)
        assertEquals(Duration.ofMinutes(1), Duration.between(hour[0].at, hour[1].at))
        assertEquals(48, day.size)
        assertEquals(Instant.parse("2026-11-24T09:00:00Z"), day.last().at)
        assertEquals(Duration.ofMinutes(30), Duration.between(day[0].at, day[1].at))
        assertTrue(hour.all { (it.cpuPercent ?: -1.0) in 0.0..100.0 })
    }

    @Test
    fun `모의 구현은 같은 VM이면 같은 값을 준다`() {
        assertEquals(fake.getMetrics(VM_ID, VmMetricsPeriod.HOUR), fake.getMetrics(VM_ID, VmMetricsPeriod.HOUR))
    }

    @Test
    fun `모의 구현은 넣은 값과 지정한 오류를 그대로 쓰고 reset하면 되돌린다`() {
        val sample = VmMetricSample(now, null, null, null, null, null, null)
        fake.put(VM_ID, listOf(sample))
        assertEquals(listOf(sample), fake.getMetrics(VM_ID, VmMetricsPeriod.DAY))

        fake.failWith(BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE))
        assertThrows<BusinessException> { fake.getMetrics(VM_ID, VmMetricsPeriod.HOUR) }

        fake.reset()
        assertEquals(60, fake.getMetrics(VM_ID, VmMetricsPeriod.HOUR).size)
    }

    @Test
    fun `운영용 구현은 모의 값을 내보내지 않고 항상 503이다`() {
        val error = assertThrows<BusinessException> { UnavailableVmMetricsInfraClient().getMetrics(VM_ID, VmMetricsPeriod.HOUR) }

        assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, error.errorCode)
    }

    @Test
    fun `프로필에 따라 모의 구현과 운영용 구현 중 하나만 뜬다`() {
        val runner =
            ApplicationContextRunner().withUserConfiguration(
                FakeVmMetricsInfraClient::class.java,
                UnavailableVmMetricsInfraClient::class.java,
            )

        runner.withInitializer { it.environment.setActiveProfiles("local") }.run { context ->
            assertIs<FakeVmMetricsInfraClient>(context.getBean(VmMetricsInfraClient::class.java))
        }
        runner.withInitializer { it.environment.setActiveProfiles("prod") }.run { context ->
            assertIs<UnavailableVmMetricsInfraClient>(context.getBean(VmMetricsInfraClient::class.java))
        }
    }

    private companion object {
        const val VM_ID = "vm-k3m9x2p7q4ta"
    }
}
