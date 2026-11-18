package com.gsmsv.api.operation.service

import com.gsmsv.api.global.authorization.OwnedResource
import com.gsmsv.api.global.authorization.SubResource
import com.gsmsv.api.global.grn.GrnParser
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.infra.job.InfraJob
import com.gsmsv.api.operation.domain.Operation
import com.gsmsv.api.operation.domain.OperationError
import com.gsmsv.api.operation.domain.OperationStage
import com.gsmsv.api.operation.domain.OperationStatus
import com.gsmsv.api.operation.domain.OperationType
import com.gsmsv.api.operation.repository.OperationRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

private val log = KotlinLogging.logger {}

/**
 * 오래 걸리는 작업의 등록과 상태 갱신.
 *
 * 도메인 흐름: 로더로 대상 자원을 꺼낸다 → [register] → operation id를 `Idempotency-Key`로 Infra에 작업을 맡긴다
 * → 받은 작업 id로 [attachJob] → `202` + `{ operation, resource }`로 답한다. 그 뒤 상태는 폴러가 [applyJob]으로 옮겨 적는다.
 */
@Service
class OperationService(
    private val operationRepository: OperationRepository,
    private val completionHandlers: List<OperationCompletionHandler>,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** 서비스 자원에 대한 작업을 등록한다. [target]은 로더가 돌려준 자원이어야 한다 */
    @Transactional
    fun register(
        type: OperationType,
        target: OwnedResource,
        createdBy: String,
    ): Operation = save(type, GrnParser.format(target.owner.id, target.id), target, createdBy)

    /** 하위 자원(스냅샷·백업 등)에 대한 작업을 등록한다. [parent]와 [child]는 하위 자원 로더가 돌려준 값이어야 한다 */
    @Transactional
    fun register(
        type: OperationType,
        parent: OwnedResource,
        child: SubResource,
        createdBy: String,
    ): Operation {
        require(child.parentId == parent.id) { "하위 자원의 부모가 다릅니다." }
        return save(type, GrnParser.formatSubResource(parent.owner.id, parent.id, child.id), parent, createdBy)
    }

    @Transactional
    fun attachJob(
        operationId: String,
        jobId: String,
    ) {
        find(operationId).attachJob(jobId, clock.instant())
    }

    /**
     * Infra 작업 상태를 operation에 옮겨 적는다. 바뀐 것이 없거나 이미 끝난 operation이면 아무 일도 하지 않는다.
     * 끝난 상태가 되면 [OperationCompletionHandler]를 부른다.
     */
    @Transactional
    fun applyJob(
        operationId: String,
        job: InfraJob,
    ) {
        val operation = find(operationId)
        check(operation.jobId == job.jobId) { "operation에 붙은 작업이 아닙니다." }
        val status = OperationStatus.fromValue(job.status)
        val stage = OperationStage.fromValue(job.stage)
        if (status == null || stage == null) {
            // 계약에 없는 값은 화면에 내보내지 않는다. 다음 폴링에서 다시 본다
            log.warn { "Infra가 계약에 없는 작업 상태를 보냈다: operationId=$operationId, status=${job.status}, stage=${job.stage}" }
            return
        }
        val error = if (status == OperationStatus.FAILED) toUserError(operationId, job) else null
        report(operation, status, stage, job.queuePosition, error)
    }

    /**
     * Infra가 작업 기록을 잃었을 때(재시작 등) operation을 실패로 마무리한다.
     *
     * 계약은 대상 자원의 실제 상태를 Infra에 물어 마무리하라고 한다 (internal-api-rules 4.2). 실제 상태 조회 API가
     * 아직 명세에 없어 지금은 다시 시도할 수 있는 실패로 둔다. VM 상태 조회가 생기면 [OperationCompletionHandler]와 함께 바꾼다.
     */
    @Transactional
    fun markJobLost(operationId: String) {
        val operation = find(operationId)
        log.warn {
            "Infra 작업 기록이 없어 실패로 마무리한다: operationId=$operationId, type=${operation.type.value}, " +
                "jobId=${operation.jobId}, status=${operation.status.value}, stage=${operation.stage.value}"
        }
        report(operation, OperationStatus.FAILED, operation.stage, operation.queuePosition, JOB_LOST)
    }

    private fun save(
        type: OperationType,
        targetGrn: String,
        target: OwnedResource,
        createdBy: String,
    ): Operation =
        operationRepository.save(
            Operation(
                id = IdGenerator.operationId(),
                type = type,
                targetGrn = targetGrn,
                targetOwner = target.owner,
                createdBy = createdBy,
                initialCreatedAt = clock.instant(),
            ),
        )

    private fun report(
        operation: Operation,
        status: OperationStatus,
        stage: OperationStage,
        queuePosition: Int,
        error: OperationError?,
    ) {
        val changed = operation.report(status, stage, queuePosition, error, clock.instant())
        if (changed && status.finished) {
            completionHandlers.filter { operation.type in it.types }.forEach { it.onFinished(operation) }
        }
    }

    private fun toUserError(
        operationId: String,
        job: InfraJob,
    ): OperationError {
        // Infra code와 details는 구현 값이라 로그에만 남긴다 (Kotlin·Go 경계 6.4: 사용자 문구는 Kotlin이 만든다)
        log.warn { "Infra 작업 실패: operationId=$operationId, code=${job.error?.code}, retryable=${job.error?.retryable}" }
        val retryable = job.error?.retryable ?: false
        return if (retryable) RETRYABLE_FAILURE else PERMANENT_FAILURE
    }

    private fun find(operationId: String): Operation =
        operationRepository.findById(operationId).orElseThrow { IllegalStateException("operation이 없습니다: $operationId") }

    private companion object {
        val RETRYABLE_FAILURE = OperationError("OPERATION_FAILED", "작업을 마치지 못했습니다. 잠시 뒤 다시 시도해 주세요.", retryable = true)
        val PERMANENT_FAILURE = OperationError("OPERATION_FAILED", "작업을 마치지 못했습니다. 관리자에게 문의해 주세요.", retryable = false)
        val JOB_LOST = OperationError("OPERATION_INTERRUPTED", "작업이 중간에 끊겼습니다. 잠시 뒤 다시 시도해 주세요.", retryable = true)
    }
}
