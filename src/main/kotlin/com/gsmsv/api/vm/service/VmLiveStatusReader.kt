package com.gsmsv.api.vm.service

import com.gsmsv.api.infra.vm.VmLiveState
import com.gsmsv.api.infra.vm.VmPower
import com.gsmsv.api.infra.vm.VmStateInfraClient
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.domain.VmStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

enum class VmPowerState {
    RUNNING,
    STOPPED,
    UNKNOWN,
}

/** 목록·상세에 덧붙이는 실시간 값. [usage]는 [powerState]가 [VmPowerState.UNKNOWN]이면 null이다 */
data class VmLiveStatus(
    val powerState: VmPowerState,
    val usage: VmLiveState?,
) {
    companion object {
        val UNKNOWN = VmLiveStatus(VmPowerState.UNKNOWN, null)
    }
}

/**
 * VM 실시간 값을 짧게 캐시해 읽는다 (Contracts-V2 `openapi/vm.yaml`의 `VmPowerState`, 10초).
 *
 * 한 VM의 조회가 실패해도 예외를 던지지 않고 그 VM만 [VmLiveStatus.UNKNOWN]이다. 목록 전체를 실패시키지 않으려는 것이다.
 * 실패도 같은 시간 캐시한다. 가상화 서버가 멈췄을 때 목록을 열 때마다 VM 수만큼 다시 부르지 않게 하려는 것이다.
 * 권한 판단을 거친 [Vm]만 받는다.
 */
@Component
class VmLiveStatusReader(
    private val client: VmStateInfraClient,
    private val clock: Clock = Clock.systemUTC(),
) {
    private data class Entry(
        val status: VmLiveStatus,
        val expiresAt: Instant,
    )

    private val cache = ConcurrentHashMap<String, Entry>()

    fun read(vm: Vm): VmLiveStatus {
        // 아직 가상화 서버에 VM이 없거나 만들지 못한 상태라 물어볼 대상이 없다
        if (vm.status == VmStatus.CREATING || vm.status == VmStatus.FAILED) return VmLiveStatus.UNKNOWN
        val now = clock.instant()
        cache[vm.id]?.takeIf { it.expiresAt.isAfter(now) }?.let { return it.status }
        val status = fetch(vm.id)
        cache.entries.removeIf { !it.value.expiresAt.isAfter(now) }
        cache[vm.id] = Entry(status, now.plus(TTL))
        return status
    }

    private fun fetch(vmId: String): VmLiveStatus =
        try {
            client.getState(vmId)?.let { VmLiveStatus(it.power.toPowerState(), it) } ?: VmLiveStatus.UNKNOWN
        } catch (e: Exception) {
            log.warn { "VM 실시간 상태를 받지 못했습니다: vmId=$vmId, error=${e.javaClass.simpleName}" }
            VmLiveStatus.UNKNOWN
        }

    private fun VmPower.toPowerState() =
        when (this) {
            VmPower.RUNNING -> VmPowerState.RUNNING
            VmPower.STOPPED -> VmPowerState.STOPPED
        }

    private companion object {
        val TTL: Duration = Duration.ofSeconds(10)
    }
}
