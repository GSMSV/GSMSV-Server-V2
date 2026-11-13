package com.gsmsv.api.infra.firewall

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
class FakeFirewallInfraClient : FirewallInfraClient {
    data class FirewallSyncCall(
        val vmId: String,
        val internalIp: String,
        val enabled: Boolean,
        val rules: List<VmFirewallRule>,
    )

    val syncCalls: MutableList<FirewallSyncCall> = CopyOnWriteArrayList()

    private val stateByVm = ConcurrentHashMap<String, FirewallSyncCall>()
    private val failure = AtomicReference<BusinessException?>(null)

    /** VM별로 마지막에 받은 규칙 목록 */
    fun currentRules(vmId: String): List<VmFirewallRule> = stateByVm[vmId]?.rules.orEmpty()

    /** VM별로 마지막에 받은 켜짐 여부. 받은 적이 없으면 false */
    fun isEnabled(vmId: String): Boolean = stateByVm[vmId]?.enabled ?: false

    fun failWith(exception: BusinessException?) = failure.set(exception)

    fun reset() {
        syncCalls.clear()
        stateByVm.clear()
        failure.set(null)
    }

    override fun syncVmFirewall(
        vmId: String,
        internalIp: String,
        enabled: Boolean,
        rules: List<VmFirewallRule>,
    ): FirewallSyncResult {
        failure.get()?.let { throw it }
        val call = FirewallSyncCall(vmId, internalIp, enabled, rules)
        syncCalls += call
        stateByVm[vmId] = call
        return FirewallSyncResult(applied = true)
    }
}
