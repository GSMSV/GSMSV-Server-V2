package com.gsmsv.api.infra.node

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Go 노드 내부 API 클라이언트 (Internal 13.8 노드 현황).
 *
 * **(미정)** 칸 이름은 열린 Contracts-V2 PR #26을 따른다. 병합 때 바뀌면 맞춘다.
 * Infra는 총량·실사용량·설정 사양 합계·클론 대기 줄만 준다. 할당 기준 여유와 노드 선택은 Server가
 * DB 할당 합계와 할당량 정책(27번)으로 계산한다 (경계 4장 레드라인).
 * 권한 판단은 하지 않는다. 호출은 로더를 통과한 service에서만 한다.
 * 실패는 [com.gsmsv.api.global.exception.BusinessException]으로 던진다 (`GATEWAY_SYNC_FAILED` 502, `UPSTREAM_UNAVAILABLE` 503).
 */
interface NodeInfraClient {
    /**
     * Infra가 맡은 노드 전부. 닿지 않는 노드는 [InfraNodeStatus.online]이 false이고 총량·사용량이 null이다.
     * [refresh]가 true면 Infra 상태 캐시를 건너뛴다 (VM 만들기 직전처럼 정확도가 중요할 때).
     */
    fun listNodes(refresh: Boolean = false): List<InfraNodeStatus>
}

/** Infra `NodeStatus`. 노드 이름과 사양은 운영 정보라 관리자 응답에만 옮긴다 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class InfraNodeStatus(
    @param:JsonProperty("node") val node: String,
    @param:JsonProperty("online") val online: Boolean,
    @param:JsonProperty("observed_at") val observedAt: Instant,
    @param:JsonProperty("total") val total: InfraNodeTotal? = null,
    @param:JsonProperty("usage") val usage: InfraNodeUsage? = null,
    @param:JsonProperty("configured") val configured: InfraNodeConfigured? = null,
    @param:JsonProperty("clone_queue_length") val cloneQueueLength: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfraNodeTotal(
    @param:JsonProperty("cpu_cores") val cpuCores: Int,
    @param:JsonProperty("cpu_threads") val cpuThreads: Int,
    @param:JsonProperty("memory_mb") val memoryMb: Long,
    @param:JsonProperty("ssd_gb") val ssdGb: Long,
)

/** [cpuPercent]는 0~100(%)이다 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class InfraNodeUsage(
    @param:JsonProperty("cpu_percent") val cpuPercent: Double,
    @param:JsonProperty("memory_used_mb") val memoryUsedMb: Long,
    @param:JsonProperty("uptime_sec") val uptimeSec: Long,
)

/** 그 노드의 모든 VM(관리 밖 VM 포함) 설정 사양 합계. Server는 DB 관리 VM 합계를 빼 관리 밖 몫을 구한다 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class InfraNodeConfigured(
    @param:JsonProperty("vcpu") val vcpu: Int,
    @param:JsonProperty("memory_mb") val memoryMb: Long,
    @param:JsonProperty("ssd_gb") val ssdGb: Long,
)
