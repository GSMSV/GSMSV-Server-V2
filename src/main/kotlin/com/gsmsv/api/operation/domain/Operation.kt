package com.gsmsv.api.operation.domain

import com.gsmsv.api.global.authorization.OperationRecord
import com.gsmsv.api.global.authorization.Owner
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Duration
import java.time.Instant

/**
 * 오래 걸리는 작업 1건. 진실은 Server DB에 있고, Infra 작업 상태는 폴링해서 옮겨 적는다 (Kotlin·Go 경계 6.1).
 *
 * 대상 자원([targetGrn])과 그 소유자는 등록할 때 로더가 돌려준 자원의 DB 값으로 정하고 바꾸지 않는다.
 * 끝난 상태가 되면 더는 바뀌지 않는다.
 */
@Entity
@Table(name = "operations")
class Operation(
    @Id
    @Column(length = 15, nullable = false)
    override val id: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    val type: OperationType,
    @Column(name = "target_grn", nullable = false, updatable = false, length = 80)
    val targetGrn: String,
    targetOwner: Owner,
    @Column(name = "created_by", nullable = false, updatable = false, length = 17)
    val createdBy: String,
    initialCreatedAt: Instant,
) : OperationRecord {
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, updatable = false, length = 8)
    private val ownerType: OwnerType =
        when (targetOwner) {
            is Owner.User -> OwnerType.USER
            is Owner.Team -> OwnerType.TEAM
        }

    @Column(name = "owner_id", nullable = false, updatable = false, length = 17)
    private val ownerId: String = targetOwner.id

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    final var status: OperationStatus = OperationStatus.QUEUED
        private set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    final var stage: OperationStage = OperationStage.QUEUED
        private set

    @Column(name = "queue_position", nullable = false)
    final var queuePosition: Int = 0
        private set

    /** Infra가 돌려준 작업 id. 사용자에게 보이지 않는다 */
    @Column(name = "job_id", length = 64)
    final var jobId: String? = null
        private set

    @Column(name = "error_code", length = 64)
    private var errorCode: String? = null

    @Column(name = "error_message", columnDefinition = "text")
    private var errorMessage: String? = null

    @Column(name = "error_retryable")
    private var errorRetryable: Boolean? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = initialCreatedAt

    @Column(name = "updated_at", nullable = false)
    final var updatedAt: Instant = initialCreatedAt
        private set

    @Column(name = "finished_at")
    final var finishedAt: Instant? = null
        private set

    override val targetOwner: Owner
        get() =
            when (ownerType) {
                OwnerType.USER -> Owner.User(ownerId)
                OwnerType.TEAM -> Owner.Team(ownerId)
            }

    val error: OperationError?
        get() {
            val code = errorCode ?: return null
            return OperationError(code, errorMessage.orEmpty(), errorRetryable ?: false)
        }

    /** Infra에 작업을 맡기고 받은 작업 id를 붙인다. 같은 id를 다시 붙이면 아무 일도 하지 않는다 */
    fun attachJob(
        jobId: String,
        at: Instant,
    ) {
        if (this.jobId == jobId) return
        check(this.jobId == null) { "이미 다른 Infra 작업이 붙은 operation입니다." }
        check(!status.finished) { "끝난 operation에는 Infra 작업을 붙일 수 없습니다." }
        this.jobId = jobId
        updatedAt = at
    }

    /**
     * 상태를 바꾼다. 이미 끝났거나 바뀐 값이 없으면 아무 일도 하지 않고 false를 돌려준다.
     *
     * @param error [OperationStatus.FAILED]일 때만 넘긴다
     */
    fun report(
        status: OperationStatus,
        stage: OperationStage,
        queuePosition: Int,
        error: OperationError?,
        at: Instant,
    ): Boolean {
        require((status == OperationStatus.FAILED) == (error != null)) { "실패한 작업에만 원인이 있습니다." }
        require(queuePosition >= 0) { "대기 순서는 0 이상입니다." }
        if (this.status.finished) return false
        // 계약상 대기 순서는 queued일 때만 뜻이 있고 그 밖에는 0이다
        val position = if (status == OperationStatus.QUEUED) queuePosition else 0
        if (this.status == status && this.stage == stage && this.queuePosition == position) return false

        this.status = status
        this.stage = stage
        this.queuePosition = position
        errorCode = error?.code
        errorMessage = error?.message
        errorRetryable = error?.retryable
        updatedAt = at
        if (status.finished) finishedAt = at
        return true
    }

    /** 끝난 지 [retention]이 지났으면 더는 조회할 수 없다 */
    fun isExpired(
        now: Instant,
        retention: Duration,
    ): Boolean = finishedAt?.let { !it.plus(retention).isAfter(now) } ?: false

    enum class OwnerType {
        USER,
        TEAM,
    }
}
