package com.gsmsv.api.infra.vm

/**
 * 가상화 서버가 보고한 VM 실시간 상태 조회.
 *
 * **(미정)** 받을 Go 내부 API는 아직 정하지 않았다 (Contracts-V2 PR #28). 그전까지 `dev`·`local`은 모의 구현을 쓰고,
 * 그 밖의 프로필은 항상 [com.gsmsv.api.global.exception.BusinessException] `UPSTREAM_UNAVAILABLE`을 던진다.
 * 권한 판단은 하지 않는다. 호출은 로더를 통과한 service에서만 한다.
 */
interface VmStateInfraClient {
    /**
     * 가상화 서버에 그 VM이 없으면 null이다.
     *
     * @throws com.gsmsv.api.global.exception.BusinessException 가상화 서버가 응답하지 않으면
     */
    fun getState(vmId: String): VmLiveState?
}

enum class VmPower {
    RUNNING,
    STOPPED,
}

/** 사용률은 0~100(%)이다. 꺼져 있으면 [uptimeSec]은 0이다 */
data class VmLiveState(
    val power: VmPower,
    val cpuPercent: Double,
    val memoryPercent: Double,
    val uptimeSec: Long,
)
