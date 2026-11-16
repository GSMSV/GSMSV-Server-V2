package com.gsmsv.api.infra.network

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Go 네트워크 내부 API 클라이언트 (Internal 13.6 포트 집합, 13.7 HTTPS 라우트). 둘 다 선언형이다.
 *
 * 부르는 쪽이 DB 값으로 원하는 상태 전체를 만들어 넘기고, 클라이언트는 그대로 보낸다. 차이 계산과 잔존 룰 정리는 Go가 한다.
 * 권한 판단은 하지 않는다. 호출은 로더를 통과한 service에서만 한다.
 * 실패는 [com.gsmsv.api.global.exception.BusinessException]으로 던진다
 * (`GATEWAY_SYNC_FAILED` 502, `UPSTREAM_UNAVAILABLE` 503).
 */
interface NetworkInfraClient {
    /** VM의 포트 집합 전체를 맞춘다. [ports]가 비어 있으면 전부 회수한다 (VM 삭제 때) */
    fun syncVmPorts(
        vmId: String,
        internalIp: String,
        ports: List<VmPortRule>,
    ): PortSyncResult

    /** 활성 도메인 전체 목록으로 HTTPS 라우트를 맞춘다 */
    fun syncHttpsRoutes(routes: List<HttpsRoute>): RouteSyncResult
}

enum class PortProtocol(
    @get:JsonValue val wire: String,
) {
    TCP("tcp"),
    UDP("udp"),
}

data class VmPortRule(
    @param:JsonProperty("id") val id: String,
    @param:JsonProperty("external") val external: Int,
    @param:JsonProperty("internal") val internal: Int,
    @param:JsonProperty("protocol") val protocol: PortProtocol,
    /** IP 또는 CIDR. 없으면 모두 허용 */
    @param:JsonProperty("allowed_source") val allowedSource: String? = null,
)

data class HttpsRoute(
    @param:JsonProperty("id") val id: String,
    @param:JsonProperty("host") val host: String,
    @param:JsonProperty("upstream") val upstream: String,
)

// added·removed의 형식(개수/목록)은 명세에 없다. 개수로 받고, Go 명세가 정해지면 맞춘다
@JsonIgnoreProperties(ignoreUnknown = true)
data class PortSyncResult(
    @param:JsonProperty("applied") val applied: Boolean,
    @param:JsonProperty("added") val added: Int = 0,
    @param:JsonProperty("removed") val removed: Int = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RouteSyncResult(
    @param:JsonProperty("applied") val applied: Boolean,
)
