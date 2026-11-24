package com.gsmsv.api.vm.dto

import java.time.Instant

/** 4.8 VM 사용량 그래프 (Contracts-V2 `VmMetrics`) */
data class VmMetricsResponse(
    val period: String,
    val points: List<VmMetricPointResponse>,
)

/** Contracts-V2 `VmMetricPoint`. 측정값이 없는 칸은 0이 아니라 null이다 */
data class VmMetricPointResponse(
    val at: Instant,
    val cpuPercent: Double?,
    val memoryMb: Double?,
    val netInBps: Double?,
    val netOutBps: Double?,
    val diskReadBps: Double?,
    val diskWriteBps: Double?,
)
