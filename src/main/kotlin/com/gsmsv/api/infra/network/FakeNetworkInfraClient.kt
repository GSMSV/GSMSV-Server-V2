package com.gsmsv.api.infra.network

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.infra.config.InfraProfiles
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Go 연동 전까지 쓰는 모의 구현 (`dev`·`local`). 받은 요청을 기억해 테스트에서 검사할 수 있다.
 * [failWith]를 지정하면 다음 호출부터 그 오류로 실패한다.
 */
@Component
@Profile(InfraProfiles.MOCK)
class FakeNetworkInfraClient : NetworkInfraClient {
    data class PortSyncCall(
        val vmId: String,
        val internalIp: String,
        val ports: List<VmPortRule>,
    )

    val portSyncCalls: MutableList<PortSyncCall> = CopyOnWriteArrayList()
    val routeSyncCalls: MutableList<List<HttpsRoute>> = CopyOnWriteArrayList()

    private val portsByVm = ConcurrentHashMap<String, List<VmPortRule>>()
    private val failure = AtomicReference<BusinessException?>(null)

    /** VM별로 마지막에 받은 포트 집합 */
    fun currentPorts(vmId: String): List<VmPortRule> = portsByVm[vmId].orEmpty()

    /** 마지막에 받은 HTTPS 라우트 전체 목록 */
    fun currentRoutes(): List<HttpsRoute> = routeSyncCalls.lastOrNull().orEmpty()

    fun failWith(exception: BusinessException?) = failure.set(exception)

    fun reset() {
        portSyncCalls.clear()
        routeSyncCalls.clear()
        portsByVm.clear()
        failure.set(null)
    }

    override fun syncVmPorts(
        vmId: String,
        internalIp: String,
        ports: List<VmPortRule>,
    ): PortSyncResult {
        failure.get()?.let { throw it }
        portSyncCalls += PortSyncCall(vmId, internalIp, ports)
        val before =
            portsByVm
                .put(vmId, ports)
                .orEmpty()
                .map { it.id }
                .toSet()
        val after = ports.map { it.id }.toSet()
        if (ports.isEmpty()) portsByVm.remove(vmId)
        return PortSyncResult(applied = true, added = (after - before).size, removed = (before - after).size)
    }

    override fun syncHttpsRoutes(routes: List<HttpsRoute>): RouteSyncResult {
        failure.get()?.let { throw it }
        routeSyncCalls += routes
        return RouteSyncResult(applied = true)
    }
}
