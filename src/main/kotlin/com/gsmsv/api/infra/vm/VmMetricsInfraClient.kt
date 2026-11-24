package com.gsmsv.api.infra.vm

import java.time.Instant

/**
 * 가상화 서버가 기록한 VM 사용량 시계열 조회 (Internal 13.9).
 *
 * **(미정)** 받을 Go 내부 API는 아직 정하지 않았다 (Go 일정 W4). 그전까지 `dev`·`local`은 모의 구현을 쓰고,
 * 그 밖의 프로필은 항상 [com.gsmsv.api.global.exception.BusinessException] `UPSTREAM_UNAVAILABLE`을 던진다.
 * 권한 판단은 하지 않는다. 호출은 로더를 통과한 service에서만 한다.
 */
interface VmMetricsInfraClient {
    /**
     * 오래된 것부터 돌려준다. 가상화 서버에 그 VM이 없으면 빈 목록이다.
     *
     * @throws com.gsmsv.api.global.exception.BusinessException 가상화 서버가 응답하지 않으면
     */
    fun getMetrics(
        vmId: String,
        period: VmMetricsPeriod,
    ): List<VmMetricSample>
}

enum class VmMetricsPeriod {
    /** 최근 1시간 */
    HOUR,

    /** 최근 24시간 */
    DAY,
}

/**
 * 한 구간의 평균값. 측정값이 없는 구간(꺼져 있던 때 등)은 값이 null이다.
 * 메모리는 MiB, 네트워크·디스크는 초당 바이트다 (Contracts-V2 `VmMetricPoint`).
 */
data class VmMetricSample(
    val at: Instant,
    val cpuPercent: Double?,
    val memoryMb: Double?,
    val netInBps: Double?,
    val netOutBps: Double?,
    val diskReadBps: Double?,
    val diskWriteBps: Double?,
)
