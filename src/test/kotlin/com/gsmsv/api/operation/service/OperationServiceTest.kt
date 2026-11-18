package com.gsmsv.api.operation.service

import com.gsmsv.api.global.authorization.OwnedResource
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.SubResource
import com.gsmsv.api.infra.job.InfraJob
import com.gsmsv.api.infra.job.InfraJobError
import com.gsmsv.api.operation.domain.Operation
import com.gsmsv.api.operation.domain.OperationStage
import com.gsmsv.api.operation.domain.OperationStatus
import com.gsmsv.api.operation.domain.OperationType
import com.gsmsv.api.operation.repository.OperationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperationServiceTest {
    private val now = Instant.parse("2026-11-12T09:00:00Z")
    private val repository = mockk<OperationRepository>()
    private val handler =
        mockk<OperationCompletionHandler>(relaxed = true) {
            every { types } returns setOf(OperationType.VM_CREATE)
        }
    private val otherHandler =
        mockk<OperationCompletionHandler>(relaxed = true) {
            every { types } returns setOf(OperationType.DATABASE_CREATE)
        }
    private val service = OperationService(repository, listOf(handler, otherHandler), Clock.fixed(now, ZoneOffset.UTC))

    private val vm = Target("vm-k3m9x2p7q4ta", Owner.Team(TEAM))

    private fun stored(): Operation {
        val operation = Operation(OPERATION_ID, OperationType.VM_CREATE, "grn:$TEAM:vm-k3m9x2p7q4ta", Owner.Team(TEAM), ME, now)
        operation.attachJob(JOB_ID, now)
        every { repository.findById(OPERATION_ID) } returns Optional.of(operation)
        return operation
    }

    private fun job(
        status: String,
        stage: String,
        queuePosition: Int = 0,
        error: InfraJobError? = null,
    ) = InfraJob(JOB_ID, "vm.create", "vm-k3m9x2p7q4ta", status, stage, queuePosition, error)

    @Test
    fun `서비스 자원 작업은 DB 소유자로 만든 GRN과 소유자를 남긴다`() {
        val saved = slot<Operation>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        val operation = service.register(OperationType.VM_CREATE, vm, ME)

        assertTrue(operation.id.startsWith("op-"))
        assertEquals("grn:$TEAM:vm-k3m9x2p7q4ta", operation.targetGrn)
        assertEquals(Owner.Team(TEAM), operation.targetOwner)
        assertEquals(OperationStatus.QUEUED, operation.status)
    }

    @Test
    fun `하위 자원 작업은 하위 자원 표기를 남기고 부모가 다르면 거절한다`() {
        val saved = slot<Operation>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        val operation = service.register(OperationType.SNAPSHOT_CREATE, vm, Child("snap-7qz3m8k2", vm.id), ME)

        assertEquals("grn:$TEAM:vm-k3m9x2p7q4ta/snap-7qz3m8k2", operation.targetGrn)
        assertThrows<IllegalArgumentException> {
            service.register(OperationType.SNAPSHOT_CREATE, vm, Child("snap-7qz3m8k2", "vm-a2b3c4d5e6f7"), ME)
        }
    }

    @Test
    fun `Infra 작업의 상태·단계·대기 순서를 옮겨 적는다`() {
        val operation = stored()

        service.applyJob(OPERATION_ID, job("queued", "queued", queuePosition = 2))

        assertEquals(OperationStatus.QUEUED, operation.status)
        assertEquals(2, operation.queuePosition)

        service.applyJob(OPERATION_ID, job("running", "cloning"))

        assertEquals(OperationStage.CLONING, operation.stage)
        verify(exactly = 0) { handler.onFinished(any()) }
    }

    @Test
    fun `계약에 없는 상태나 단계는 옮겨 적지 않는다`() {
        val operation = stored()

        service.applyJob(OPERATION_ID, job("running", "warming_up"))
        service.applyJob(OPERATION_ID, job("paused", "cloning"))

        assertEquals(OperationStatus.QUEUED, operation.status)
        assertEquals(OperationStage.QUEUED, operation.stage)
    }

    @Test
    fun `실패하면 Infra code와 details를 숨기고 다시 시도 가능 여부만 옮긴다`() {
        val operation = stored()

        service.applyJob(OPERATION_ID, job("failed", "cloning", error = InfraJobError("VMID_TAKEN", false, mapOf("vmid" to 101))))

        val error = operation.error
        assertEquals("OPERATION_FAILED", error?.code)
        assertEquals(false, error?.retryable)
        assertEquals(OperationStage.CLONING, operation.stage)
    }

    @Test
    fun `끝나면 그 작업 종류를 맡은 처리만 한 번 부른다`() {
        val operation = stored()

        service.applyJob(OPERATION_ID, job("succeeded", "networking"))
        service.applyJob(OPERATION_ID, job("succeeded", "networking"))

        verify(exactly = 1) { handler.onFinished(operation) }
        verify(exactly = 0) { otherHandler.onFinished(any()) }
    }

    @Test
    fun `다른 Infra 작업의 상태는 받지 않는다`() {
        stored()

        assertThrows<IllegalStateException> {
            service.applyJob(OPERATION_ID, job("running", "cloning").copy(jobId = "job-6g7h8j9k2m3n"))
        }
    }

    @Test
    fun `Infra가 작업 기록을 잃으면 다시 시도할 수 있는 실패로 마무리한다`() {
        val operation = stored()
        service.applyJob(OPERATION_ID, job("running", "booting"))

        service.markJobLost(OPERATION_ID)

        assertEquals(OperationStatus.FAILED, operation.status)
        assertEquals(OperationStage.BOOTING, operation.stage)
        assertEquals(true, operation.error?.retryable)
        verify(exactly = 1) { handler.onFinished(operation) }
    }

    @Test
    fun `진행 중인 작업은 끝난 시각이 없다`() {
        val operation = stored()

        service.applyJob(OPERATION_ID, job("running", "cloning"))

        assertNull(operation.finishedAt)
    }

    private class Target(
        override val id: String,
        override val owner: Owner,
    ) : OwnedResource

    private class Child(
        override val id: String,
        override val parentId: String,
    ) : SubResource

    private companion object {
        const val ME = "user-a1b2c3d4e5f6"
        const val TEAM = "team-4p8w2k9m1xza"
        const val OPERATION_ID = "op-7w8k9q0p1a2s"
        const val JOB_ID = "job-5f6g7h8j9k2m"
    }
}
