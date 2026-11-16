package com.gsmsv.api.infra.network

import com.fasterxml.jackson.annotation.JsonProperty
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.infra.GoApiCaller
import com.gsmsv.api.infra.config.InfraProfiles
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

private val log = KotlinLogging.logger {}

/** 실제 Go 연동. `dev`·`local`이 아닌 프로필에서 쓴다 */
@Component
@Profile(InfraProfiles.REAL)
class HttpNetworkInfraClient(
    goRestClient: RestClient,
) : NetworkInfraClient {
    private val caller = GoApiCaller(goRestClient)

    override fun syncVmPorts(
        vmId: String,
        internalIp: String,
        ports: List<VmPortRule>,
    ): PortSyncResult {
        val result =
            caller.put(
                operation = "network.vm-ports",
                uri = "/network/vms/{vmId}/ports",
                uriVariables = mapOf("vmId" to vmId),
                body = VmPortsRequest(internalIp, ports),
                responseType = PortSyncResult::class.java,
            )
        if (!result.applied) {
            log.warn { "Go가 포트 집합을 적용하지 않았다: vmId=$vmId" }
            throw BusinessException(ErrorCode.GATEWAY_SYNC_FAILED)
        }
        return result
    }

    override fun syncHttpsRoutes(routes: List<HttpsRoute>): RouteSyncResult {
        val result =
            caller.put(
                operation = "network.https-routes",
                uri = "/https/routes",
                uriVariables = emptyMap(),
                body = HttpsRoutesRequest(routes),
                responseType = RouteSyncResult::class.java,
            )
        if (!result.applied) {
            log.warn { "Go가 HTTPS 라우트를 적용하지 않았다" }
            throw BusinessException(ErrorCode.GATEWAY_SYNC_FAILED)
        }
        return result
    }

    private data class VmPortsRequest(
        @param:JsonProperty("internal_ip") val internalIp: String,
        @param:JsonProperty("ports") val ports: List<VmPortRule>,
    )

    private data class HttpsRoutesRequest(
        @param:JsonProperty("routes") val routes: List<HttpsRoute>,
    )
}
