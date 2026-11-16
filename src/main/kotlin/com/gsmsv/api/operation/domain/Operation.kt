package com.gsmsv.api.operation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

/**
 * 오래 걸리는 작업 하나 (C7). 요청은 접수하자마자 202로 답하고 일은 뒤에서 이어서 한다.
 *
 * 대상 자원은 종류([resourceType], 예: `db`)와 id로 가리킨다. 상태는 메서드로만 바꾼다.
 */
@Entity
@Table(name = "operations")
class Operation(
    @Id
    @Column(length = 15, nullable = false)
    val id: String,
    /** 작업 종류. 앞부분이 대상 자원의 접두사와 같다 (예: `db.create`) */
    @Column(nullable = false, updatable = false, length = 32)
    val type: String,
    @Column(name = "resource_type", nullable = false, updatable = false, length = 8)
    val resourceType: String,
    @Column(name = "resource_id", nullable = false, updatable = false, length = 15)
    val resourceId: String,
    @Column(name = "requested_by", nullable = false, updatable = false, length = 17)
    val requestedBy: String,
    initialCreatedAt: Instant = Instant.now(),
) {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    final var status: OperationStatus = OperationStatus.RUNNING
        private set

    /** 진행 단계. 단계를 나누지 않는 작업은 `running` 하나다. 끝나면 비운다 */
    @Column(length = 16)
    final var stage: String? = STAGE_RUNNING
        private set

    @Column(name = "error_code", length = 64)
    final var errorCode: String? = null
        private set

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = initialCreatedAt

    @Column(name = "updated_at", nullable = false)
    final var updatedAt: Instant = initialCreatedAt
        private set

    @Column(name = "finished_at")
    final var finishedAt: Instant? = null
        private set

    fun succeed(at: Instant = Instant.now()) {
        check(status == OperationStatus.RUNNING) { "진행 중인 작업만 성공으로 끝낼 수 있습니다." }
        status = OperationStatus.SUCCEEDED
        stage = null
        finishedAt = at
    }

    /** 실패 원인은 오류 `code`만 남긴다. 접속 주소 같은 내부 정보는 담지 않는다 */
    fun fail(
        code: String,
        at: Instant = Instant.now(),
    ) {
        check(status == OperationStatus.RUNNING) { "진행 중인 작업만 실패로 끝낼 수 있습니다." }
        status = OperationStatus.FAILED
        errorCode = code
        stage = null
        finishedAt = at
    }

    @PreUpdate
    fun updateTimestamp() {
        updatedAt = Instant.now()
    }

    companion object {
        const val STAGE_RUNNING = "running"
    }
}
