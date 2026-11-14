package com.gsmsv.api.infra.firewall

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Go 방화벽 내부 API 클라이언트. 선언형이다.
 *
 * 부르는 쪽이 DB 값으로 VM 하나의 방화벽 상태 전체(켜짐 여부 + 규칙 목록)를 만들어 넘긴다. 차이 계산은 Go가 한다.
 * 목적지는 [internalIp]로 강제한다. 규칙 값을 가상화 서버 값으로 바꾸는 일도 Go가 한다.
 * 권한 판단은 하지 않는다. 호출은 로더를 통과한 service에서만 한다.
 *
 * Go 쪽 엔드포인트는 계약 내부 API 명세에 아직 없다 (내부 API 공통 규약 12장). 정해지면 실제 연동을 더한다.
 * 실패는 [com.gsmsv.api.global.exception.BusinessException]으로 던진다 (`UPSTREAM_UNAVAILABLE` 503 등).
 */
interface FirewallInfraClient {
    fun syncVmFirewall(
        vmId: String,
        internalIp: String,
        enabled: Boolean,
        rules: List<VmFirewallRule>,
    ): FirewallSyncResult
}

/** 값은 모두 웹 API와 같은 소문자다 (`accept`, `in`, `tcp`) */
data class VmFirewallRule(
    @param:JsonProperty("id") val id: String,
    @param:JsonProperty("action") val action: String,
    @param:JsonProperty("direction") val direction: String,
    @param:JsonProperty("protocol") val protocol: String,
    /** 단일 포트 또는 `시작:끝`. 없으면 모든 포트 */
    @param:JsonProperty("dport") val dport: String? = null,
    /** IP 또는 CIDR. 없으면 모든 출발지 */
    @param:JsonProperty("source") val source: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FirewallSyncResult(
    @param:JsonProperty("applied") val applied: Boolean,
)
