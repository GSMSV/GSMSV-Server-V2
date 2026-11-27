package com.gsmsv.api.infra.vm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.gsmsv.api.infra.job.InfraJobAccepted
import java.time.Instant

/**
 * Go VM 내부 API 클라이언트 (Internal 13.1 VM 만들기, 13.2 VM 지우기, VM 실제 상태).
 *
 * **(미정)** 칸 이름은 열린 Contracts-V2 PR #28을 따른다. 병합 때 바뀌면 맞춘다.
 * 전원(13.3)·사양(13.4)·스냅샷(13.5)은 명세가 생기면 이 인터페이스에 더한다.
 *
 * 노드 선택과 VM 번호(`vmid`) 후보는 Server가 정해 넘긴다 (경계 4장 레드라인). 클론 대기 줄은 Infra가 맡는다.
 * 권한 판단은 하지 않는다. 호출은 로더를 통과한 service에서만 한다.
 * 실패는 [com.gsmsv.api.global.exception.BusinessException]으로 던진다
 * (`CONFLICT` 409, `GATEWAY_SYNC_FAILED` 502, `UPSTREAM_UNAVAILABLE` 503). 작업 자체의 실패는 작업 상태(13.10)의 `error`로 온다.
 */
interface VmInfraClient {
    /**
     * VM 만들기 작업을 맡긴다. [idempotencyKey]에는 그 작업의 operation id(`op-…`)를 넣는다.
     * 같은 키로 다시 부르면 Infra는 처음 작업을 다시 준다.
     */
    fun createVm(
        request: InfraVmCreateRequest,
        idempotencyKey: String,
    ): InfraJobAccepted

    /**
     * VM 지우기 작업을 맡긴다. Infra에 그 VM이 없으면 null이다. 부르는 쪽은 이미 지워진 것으로 보고 끝낸다.
     * 만드는 중인 VM은 409(`CONFLICT`)다. 먼저 만들기 작업을 취소해야 한다.
     */
    fun deleteVm(
        vmId: String,
        idempotencyKey: String,
    ): InfraJobAccepted?

    /** Proxmox에 있는 VM의 지금 상태. 작업 기록을 잃었을 때 operation을 마무리하는 데 쓴다. 없으면 null이다 */
    fun getVm(vmId: String): InfraVmState?
}

data class InfraVmCreateRequest(
    @param:JsonProperty("id") val id: String,
    @param:JsonProperty("node") val node: String,
    @param:JsonProperty("vmid") val vmid: Int,
    @param:JsonProperty("image") val image: String,
    @param:JsonProperty("vcpu") val vcpu: Int,
    @param:JsonProperty("memory_mb") val memoryMb: Int,
    @param:JsonProperty("ssd_gb") val ssdGb: Int,
    @param:JsonProperty("internal_ip") val internalIp: String,
    @param:JsonProperty("cloud_init") val cloudInit: InfraVmCloudInit,
)

/** 첫 부팅 때 cloud-init이 넣을 값. [password]는 평문이라 로그에 남기지 않는다 */
data class InfraVmCloudInit(
    @param:JsonProperty("user") val user: String,
    @param:JsonProperty("password") val password: String,
    @param:JsonProperty("hostname") val hostname: String,
) {
    override fun toString(): String = "InfraVmCloudInit(user=$user, hostname=$hostname)"
}

/** Infra `VmState`. [node]·[vmid]는 운영 정보라 사용자 응답에 옮기지 않는다 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class InfraVmState(
    @param:JsonProperty("id") val id: String,
    @param:JsonProperty("node") val node: String,
    @param:JsonProperty("vmid") val vmid: Int,
    @param:JsonProperty("power") val power: VmPower,
    @param:JsonProperty("vcpu") val vcpu: Int,
    @param:JsonProperty("memory_mb") val memoryMb: Int,
    @param:JsonProperty("ssd_gb") val ssdGb: Int,
    @param:JsonProperty("observed_at") val observedAt: Instant,
)
