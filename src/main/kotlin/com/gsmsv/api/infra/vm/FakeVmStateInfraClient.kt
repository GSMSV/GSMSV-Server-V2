package com.gsmsv.api.infra.vm

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.infra.config.InfraProfiles
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Go 연동 전까지 쓰는 모의 구현 (`dev`·`local`). [put]으로 넣은 상태를 그대로 돌려준다.
 * 넣지 않은 VM은 켜져 있고 사용률이 0인 것으로 본다. 화면 개발 때 모든 VM이 `unknown`으로 보이지 않게 하려는 것이다.
 * [failWith]를 지정하면 다음 호출부터 그 오류로 실패한다.
 */
@Component
@Profile(InfraProfiles.MOCK)
class FakeVmStateInfraClient : VmStateInfraClient {
    private val states = ConcurrentHashMap<String, VmLiveState>()
    private val failure = AtomicReference<BusinessException?>(null)

    fun put(
        vmId: String,
        state: VmLiveState,
    ) {
        states[vmId] = state
    }

    fun failWith(exception: BusinessException?) = failure.set(exception)

    fun reset() {
        states.clear()
        failure.set(null)
    }

    override fun getState(vmId: String): VmLiveState? {
        failure.get()?.let { throw it }
        return states[vmId] ?: DEFAULT
    }

    private companion object {
        val DEFAULT = VmLiveState(power = VmPower.RUNNING, cpuPercent = 0.0, memoryPercent = 0.0, uptimeSec = 0)
    }
}
