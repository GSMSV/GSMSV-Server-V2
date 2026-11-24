package com.gsmsv.api.vm.service

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.response.FieldErrorDetail
import com.gsmsv.api.infra.vm.VmMetricSample
import com.gsmsv.api.infra.vm.VmMetricsInfraClient
import com.gsmsv.api.infra.vm.VmMetricsPeriod
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.domain.VmStatus
import com.gsmsv.api.vm.dto.VmMetricPointResponse
import com.gsmsv.api.vm.dto.VmMetricsResponse
import org.springframework.stereotype.Service

/**
 * VM 사용량 그래프 (Instance 4.8). 로더가 돌려준 [Vm]만 받는다.
 *
 * 목록·상세와 달리 응답 전체가 가상화 서버 값이라, 받지 못하면 실패를 그대로 올린다 (503 `UPSTREAM_UNAVAILABLE`).
 * DB를 읽지 않으므로 트랜잭션을 두지 않는다.
 */
@Service
class VmMetricsService(
    private val client: VmMetricsInfraClient,
) {
    /** [period]가 null이면 `hour`다. `hour`·`day`가 아니면 400 `VALIDATION_FAILED`다 (v1처럼 `hour`로 바꾸지 않는다) */
    fun get(
        vm: Vm,
        period: String?,
    ): VmMetricsResponse {
        val parsed = parsePeriod(period)
        // 아직 가상화 서버에 VM이 없거나 만들지 못한 상태라 물어볼 대상이 없다
        val samples =
            if (vm.status == VmStatus.CREATING || vm.status == VmStatus.FAILED) {
                emptyList()
            } else {
                client.getMetrics(vm.id, parsed)
            }
        return VmMetricsResponse(period = parsed.name.lowercase(), points = samples.map(::toPoint))
    }

    private fun parsePeriod(period: String?): VmMetricsPeriod =
        when (period) {
            null, "hour" -> VmMetricsPeriod.HOUR

            "day" -> VmMetricsPeriod.DAY

            else -> throw BusinessException(
                ErrorCode.VALIDATION_FAILED,
                details = mapOf("fields" to listOf(FieldErrorDetail("period", "period는 hour 또는 day여야 합니다."))),
            )
        }

    private fun toPoint(sample: VmMetricSample) =
        VmMetricPointResponse(
            at = sample.at,
            cpuPercent = sample.cpuPercent,
            memoryMb = sample.memoryMb,
            netInBps = sample.netInBps,
            netOutBps = sample.netOutBps,
            diskReadBps = sample.diskReadBps,
            diskWriteBps = sample.diskWriteBps,
        )
}
