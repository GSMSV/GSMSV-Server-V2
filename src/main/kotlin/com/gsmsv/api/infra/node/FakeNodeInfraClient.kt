package com.gsmsv.api.infra.node

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.infra.config.InfraProfiles
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Go 연동 전까지 쓰는 모의 구현 (`dev`·`local`). [setNodes]로 넣은 노드를 그대로 돌려준다.
 * 넣지 않으면 여유가 넉넉한 노드 하나를 준다. 화면 개발 때 VM 만들기가 자리 부족으로 막히지 않게 하려는 것이다.
 * [failWith]를 지정하면 다음 호출부터 그 오류로 실패한다.
 */
@Component
@Profile(InfraProfiles.MOCK)
class FakeNodeInfraClient(
    private val clock: Clock = Clock.systemUTC(),
) : NodeInfraClient {
    /** 받은 `refresh` 값 */
    val listCalls: MutableList<Boolean> = CopyOnWriteArrayList()

    private val nodes = AtomicReference<List<InfraNodeStatus>?>(null)
    private val failure = AtomicReference<BusinessException?>(null)

    fun setNodes(value: List<InfraNodeStatus>) = nodes.set(value)

    fun failWith(exception: BusinessException?) = failure.set(exception)

    fun reset() {
        listCalls.clear()
        nodes.set(null)
        failure.set(null)
    }

    override fun listNodes(refresh: Boolean): List<InfraNodeStatus> {
        failure.get()?.let { throw it }
        listCalls += refresh
        return nodes.get() ?: listOf(defaultNode())
    }

    private fun defaultNode() =
        InfraNodeStatus(
            node = "fake-node",
            online = true,
            observedAt = Instant.now(clock),
            total = InfraNodeTotal(cpuCores = 32, cpuThreads = 64, memoryMb = 262_144, ssdGb = 4_000),
            usage = InfraNodeUsage(cpuPercent = 0.0, memoryUsedMb = 0, uptimeSec = 0),
            configured = InfraNodeConfigured(vcpu = 0, memoryMb = 0, ssdGb = 0),
            cloneQueueLength = 0,
        )
}
