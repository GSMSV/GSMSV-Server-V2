package com.gsmsv.api.infra.vm

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.infra.config.InfraProfiles
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.sin

/**
 * Go 연동 전까지 쓰는 모의 구현 (`dev`·`local`).
 *
 * [put]으로 넣지 않은 VM은 VM id로 정해지는 그럴듯한 값을 만든다. 화면을 새로 고쳐도 그래프 모양이 같게 하려는 것이다.
 * 점 간격은 지금 가상화 서버와 같게 `HOUR` 1분, `DAY` 30분이다.
 * [failWith]를 지정하면 다음 호출부터 그 오류로 실패한다.
 */
@Component
@Profile(InfraProfiles.MOCK)
class FakeVmMetricsInfraClient(
    private val clock: Clock = Clock.systemUTC(),
) : VmMetricsInfraClient {
    private val samples = ConcurrentHashMap<String, List<VmMetricSample>>()
    private val failure = AtomicReference<BusinessException?>(null)

    fun put(
        vmId: String,
        values: List<VmMetricSample>,
    ) {
        samples[vmId] = values
    }

    fun failWith(exception: BusinessException?) = failure.set(exception)

    fun reset() {
        samples.clear()
        failure.set(null)
    }

    override fun getMetrics(
        vmId: String,
        period: VmMetricsPeriod,
    ): List<VmMetricSample> {
        failure.get()?.let { throw it }
        return samples[vmId] ?: generate(vmId, period)
    }

    private fun generate(
        vmId: String,
        period: VmMetricsPeriod,
    ): List<VmMetricSample> {
        val step = stepOf(period)
        val last = Instant.ofEpochSecond(clock.instant().epochSecond / step.seconds * step.seconds)
        val count = (windowOf(period).seconds / step.seconds).toInt()
        val seed = abs(vmId.hashCode() % SEED_RANGE)
        return (count - 1 downTo 0).map { back ->
            val at = last.minus(step.multipliedBy(back.toLong()))
            val wave = (sin((at.epochSecond / step.seconds + seed) / WAVE_LENGTH) + 1) / 2
            VmMetricSample(
                at = at,
                cpuPercent = round1(BASE_CPU + wave * CPU_RANGE),
                memoryMb = round1(BASE_MEMORY_MB + wave * MEMORY_RANGE_MB),
                netInBps = round1(wave * NET_IN_MAX_BPS),
                netOutBps = round1(wave * NET_OUT_MAX_BPS),
                diskReadBps = round1((1 - wave) * DISK_MAX_BPS),
                diskWriteBps = round1(wave * DISK_MAX_BPS),
            )
        }
    }

    private fun round1(value: Double) = Math.round(value * 10) / 10.0

    private companion object {
        const val SEED_RANGE = 1000
        const val WAVE_LENGTH = 7.0
        const val BASE_CPU = 2.0
        const val CPU_RANGE = 30.0
        const val BASE_MEMORY_MB = 600.0
        const val MEMORY_RANGE_MB = 400.0
        const val NET_IN_MAX_BPS = 20_000.0
        const val NET_OUT_MAX_BPS = 12_000.0
        const val DISK_MAX_BPS = 8_192.0

        fun stepOf(period: VmMetricsPeriod): Duration =
            when (period) {
                VmMetricsPeriod.HOUR -> Duration.ofMinutes(1)
                VmMetricsPeriod.DAY -> Duration.ofMinutes(30)
            }

        fun windowOf(period: VmMetricsPeriod): Duration =
            when (period) {
                VmMetricsPeriod.HOUR -> Duration.ofHours(1)
                VmMetricsPeriod.DAY -> Duration.ofDays(1)
            }
    }
}
