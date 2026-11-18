package com.gsmsv.api.operation.domain

import com.gsmsv.api.global.authorization.Owner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperationTest {
    private val createdAt = Instant.parse("2026-11-12T09:00:00Z")
    private val later = createdAt.plusSeconds(20)
    private val error = OperationError("OPERATION_FAILED", "작업을 마치지 못했습니다.", retryable = true)

    private fun operation() =
        Operation(
            id = "op-7w8k9q0p1a2s",
            type = OperationType.VM_CREATE,
            targetGrn = "grn:team-4p8w2k9m1xza:vm-k3m9x2p7q4ta",
            targetOwner = Owner.Team("team-4p8w2k9m1xza"),
            createdBy = "user-a1b2c3d4e5f6",
            initialCreatedAt = createdAt,
        )

    @Test
    fun `새 작업은 대기 중이고 대상 소유자를 그대로 갖는다`() {
        val operation = operation()

        assertEquals(OperationStatus.QUEUED, operation.status)
        assertEquals(OperationStage.QUEUED, operation.stage)
        assertEquals(Owner.Team("team-4p8w2k9m1xza"), operation.targetOwner)
        assertNull(operation.error)
        assertNull(operation.finishedAt)
    }

    @Test
    fun `진행 단계가 바뀌면 갱신 시각을 바꾸고 true를 돌려준다`() {
        val operation = operation()

        assertTrue(operation.report(OperationStatus.RUNNING, OperationStage.CLONING, 0, null, later))

        assertEquals(OperationStage.CLONING, operation.stage)
        assertEquals(later, operation.updatedAt)
        assertNull(operation.finishedAt)
    }

    @Test
    fun `바뀐 값이 없으면 갱신 시각을 그대로 두고 false를 돌려준다`() {
        val operation = operation()
        operation.report(OperationStatus.QUEUED, OperationStage.QUEUED, 2, null, later)

        assertFalse(operation.report(OperationStatus.QUEUED, OperationStage.QUEUED, 2, null, later.plusSeconds(5)))
        assertEquals(later, operation.updatedAt)
    }

    @Test
    fun `대기 순서는 queued일 때만 남기고 그 밖에는 0이다`() {
        val operation = operation()

        operation.report(OperationStatus.QUEUED, OperationStage.QUEUED, 3, null, later)
        assertEquals(3, operation.queuePosition)

        operation.report(OperationStatus.RUNNING, OperationStage.CLONING, 3, null, later.plusSeconds(1))
        assertEquals(0, operation.queuePosition)
    }

    @Test
    fun `실패하면 원인과 끝난 시각을 남기고 실패한 단계를 그대로 둔다`() {
        val operation = operation()
        operation.report(OperationStatus.RUNNING, OperationStage.CLONING, 0, null, later)

        operation.report(OperationStatus.FAILED, OperationStage.CLONING, 0, error, later.plusSeconds(45))

        assertEquals(error, operation.error)
        assertEquals(OperationStage.CLONING, operation.stage)
        assertEquals(later.plusSeconds(45), operation.finishedAt)
    }

    @Test
    fun `끝난 작업은 더는 바뀌지 않는다`() {
        val operation = operation()
        operation.report(OperationStatus.SUCCEEDED, OperationStage.NETWORKING, 0, null, later)

        assertFalse(operation.report(OperationStatus.RUNNING, OperationStage.CLONING, 0, null, later.plusSeconds(1)))
        assertEquals(OperationStatus.SUCCEEDED, operation.status)
        assertEquals(later, operation.finishedAt)
    }

    @Test
    fun `실패가 아닌데 원인을 넘기거나 실패인데 원인이 없으면 거절한다`() {
        assertThrows<IllegalArgumentException> { operation().report(OperationStatus.SUCCEEDED, OperationStage.RUNNING, 0, error, later) }
        assertThrows<IllegalArgumentException> { operation().report(OperationStatus.FAILED, OperationStage.RUNNING, 0, null, later) }
    }

    @Test
    fun `Infra 작업 id는 한 번만 붙이고 같은 id는 다시 붙여도 된다`() {
        val operation = operation()

        operation.attachJob("job-5f6g7h8j9k2m", later)
        operation.attachJob("job-5f6g7h8j9k2m", later)

        assertEquals("job-5f6g7h8j9k2m", operation.jobId)
        assertThrows<IllegalStateException> { operation.attachJob("job-6g7h8j9k2m3n", later) }
    }

    @Test
    fun `끝난 지 보관 기간이 지나면 만료된다`() {
        val operation = operation()
        val retention = Duration.ofDays(30)
        assertFalse(operation.isExpired(later.plus(Duration.ofDays(365)), retention))

        operation.report(OperationStatus.SUCCEEDED, OperationStage.RUNNING, 0, null, later)

        assertFalse(operation.isExpired(later.plus(retention).minusSeconds(1), retention))
        assertTrue(operation.isExpired(later.plus(retention), retention))
    }
}
