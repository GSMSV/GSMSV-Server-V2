package com.gsmsv.api.infra.vm

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.infra.config.InfraProfiles
import com.gsmsv.api.infra.job.FakeJobInfraClient
import com.gsmsv.api.infra.job.InfraJob
import com.gsmsv.api.infra.job.InfraJobAccepted
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Go 연동 전까지 쓰는 모의 구현 (`dev`·`local`). 받은 요청을 기억해 테스트에서 검사할 수 있다.
 *
 * 작업은 받자마자 끝난 것으로 [FakeJobInfraClient]에 넣는다. 그래서 작업 상태를 폴링하는 쪽은 다음 조회에서 바로 끝을 본다.
 * 같은 멱등 키로 다시 부르면 처음 작업을 다시 준다. [failWith]를 지정하면 다음 호출부터 그 오류로 실패한다.
 */
@Component
@Profile(InfraProfiles.MOCK)
class FakeVmInfraClient(
    private val jobs: FakeJobInfraClient,
    private val clock: Clock = Clock.systemUTC(),
) : VmInfraClient {
    data class CreateCall(
        val request: InfraVmCreateRequest,
        val idempotencyKey: String,
    )

    data class DeleteCall(
        val vmId: String,
        val idempotencyKey: String,
    )

    val createCalls: MutableList<CreateCall> = CopyOnWriteArrayList()
    val deleteCalls: MutableList<DeleteCall> = CopyOnWriteArrayList()

    private val vms = ConcurrentHashMap<String, InfraVmState>()
    private val accepted = ConcurrentHashMap<String, InfraJobAccepted>()
    private val jobSequence = AtomicLong()
    private val failure = AtomicReference<BusinessException?>(null)

    fun failWith(exception: BusinessException?) = failure.set(exception)

    fun reset() {
        createCalls.clear()
        deleteCalls.clear()
        vms.clear()
        accepted.clear()
        failure.set(null)
    }

    override fun createVm(
        request: InfraVmCreateRequest,
        idempotencyKey: String,
    ): InfraJobAccepted {
        failure.get()?.let { throw it }
        accepted[idempotencyKey]?.let { return it }
        if (vms.containsKey(request.id)) throw BusinessException(ErrorCode.CONFLICT)

        createCalls += CreateCall(request, idempotencyKey)
        vms[request.id] =
            InfraVmState(
                id = request.id,
                node = request.node,
                vmid = request.vmid,
                power = VmPower.RUNNING,
                vcpu = request.vcpu,
                memoryMb = request.memoryMb,
                ssdGb = request.ssdGb,
                observedAt = Instant.now(clock),
            )
        return accept(idempotencyKey, type = "vm.create", resourceId = request.id, lastStage = "networking")
    }

    override fun deleteVm(
        vmId: String,
        idempotencyKey: String,
    ): InfraJobAccepted? {
        failure.get()?.let { throw it }
        accepted[idempotencyKey]?.let { return it }

        deleteCalls += DeleteCall(vmId, idempotencyKey)
        vms.remove(vmId) ?: return null
        return accept(idempotencyKey, type = "vm.delete", resourceId = vmId, lastStage = "running")
    }

    override fun getVm(vmId: String): InfraVmState? {
        failure.get()?.let { throw it }
        return vms[vmId]
    }

    private fun accept(
        idempotencyKey: String,
        type: String,
        resourceId: String,
        lastStage: String,
    ): InfraJobAccepted {
        val jobId = "job-fake${jobSequence.incrementAndGet()}"
        jobs.put(
            InfraJob(
                jobId = jobId,
                type = type,
                resourceId = resourceId,
                status = "succeeded",
                stage = lastStage,
                queuePosition = 0,
            ),
        )
        return InfraJobAccepted(jobId = jobId, status = "queued", queuePosition = 0)
            .also { accepted[idempotencyKey] = it }
    }
}
