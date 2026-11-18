package com.gsmsv.api.operation

import com.gsmsv.api.TestcontainersConfiguration
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.operation.domain.Operation
import com.gsmsv.api.operation.domain.OperationError
import com.gsmsv.api.operation.domain.OperationStage
import com.gsmsv.api.operation.domain.OperationStatus
import com.gsmsv.api.operation.domain.OperationType
import com.gsmsv.api.operation.repository.OperationRepository
import com.gsmsv.api.operation.service.OperationResourceReader
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q=",
        "gsmsv.operation.job-poll-enabled=false",
    ],
)
@Tag("integration")
@Transactional
class OperationPersistenceTest
    @Autowired
    constructor(
        private val operationRepository: OperationRepository,
        private val operationReader: OperationResourceReader,
        private val entityManager: EntityManager,
    ) {
        private val now = Instant.now().truncatedTo(ChronoUnit.MICROS)

        private fun save(owner: Owner = Owner.Team("team-4p8w2k9m1xza")): Operation =
            operationRepository.save(
                Operation(
                    IdGenerator.operationId(),
                    OperationType.VM_CREATE,
                    "grn:${owner.id}:vm-k3m9x2p7q4ta",
                    owner,
                    "user-a1b2c3d4e5f6",
                    now,
                ),
            )

        private fun reload(id: String): Operation {
            entityManager.flush()
            entityManager.clear()
            return operationRepository.findById(id).orElseThrow()
        }

        @Test
        fun `저장한 작업을 다시 꺼내면 대상 소유자와 상태를 그대로 돌려준다`() {
            val saved = save(Owner.User("user-a1b2c3d4e5f6"))

            val found = reload(saved.id)

            assertEquals(Owner.User("user-a1b2c3d4e5f6"), found.targetOwner)
            assertEquals(OperationStatus.QUEUED, found.status)
            assertEquals(OperationStage.QUEUED, found.stage)
            assertNull(found.error)
        }

        @Test
        fun `실패 원인과 끝난 시각을 저장한다`() {
            val saved = save()
            saved.attachJob("job-5f6g7h8j9k2m", now)
            saved.report(OperationStatus.FAILED, OperationStage.CLONING, 0, OperationError("OPERATION_FAILED", "실패", true), now)

            val found = reload(saved.id)

            assertEquals("job-5f6g7h8j9k2m", found.jobId)
            assertEquals(OperationError("OPERATION_FAILED", "실패", true), found.error)
            assertEquals(now, found.finishedAt)
        }

        @Test
        fun `진행 중이고 Infra에 맡긴 작업만 폴링 대상이다`() {
            val active = save().apply { attachJob("job-5f6g7h8j9k2m", now) }
            save()
            save().apply {
                attachJob("job-6g7h8j9k2m3n", now)
                report(OperationStatus.SUCCEEDED, OperationStage.NETWORKING, 0, null, now)
            }
            entityManager.flush()

            val ids = operationRepository.findAllByFinishedAtIsNullAndJobIdIsNotNull().map { it.id }

            assertEquals(listOf(active.id), ids)
        }

        @Test
        fun `보관 기간이 지난 작업은 조회에서 빠지고 정리에서 지워진다`() {
            val old = save().apply { report(OperationStatus.SUCCEEDED, OperationStage.RUNNING, 0, null, now.minus(Duration.ofDays(31))) }
            val recent = save().apply { report(OperationStatus.SUCCEEDED, OperationStage.RUNNING, 0, null, now) }
            entityManager.flush()

            assertNull(operationReader.findById(old.id))
            assertNotNull(operationReader.findById(recent.id))

            assertEquals(1, operationRepository.deleteFinishedBefore(now.minus(Duration.ofDays(30))))
        }
    }
