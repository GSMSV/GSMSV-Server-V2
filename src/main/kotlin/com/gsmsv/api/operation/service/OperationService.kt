package com.gsmsv.api.operation.service

import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.operation.domain.Operation
import com.gsmsv.api.operation.domain.OperationStatus
import com.gsmsv.api.operation.repository.OperationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 오래 걸리는 작업을 등록하고 끝낸다. 작업을 시작하는 도메인 service가 부른다.
 *
 * **(미정)** 조회 API(`GET /api/v2/operations/{id}`)와 SSE는 계약에 명세가 없어 만들지 않았다 (Server-V2 #26).
 */
@Service
class OperationService(
    private val operationRepository: OperationRepository,
) {
    /** 접수한 작업을 진행 중으로 저장한다. 호출한 쪽의 트랜잭션에 묶이므로 대상 자원과 함께 커밋된다 */
    @Transactional
    fun register(
        type: String,
        resourceType: String,
        resourceId: String,
        requestedBy: String,
    ): Operation = operationRepository.save(Operation(IdGenerator.operationId(), type, resourceType, resourceId, requestedBy))

    @Transactional
    fun succeed(operation: Operation) {
        operation.succeed()
        operationRepository.save(operation)
    }

    @Transactional
    fun fail(
        operation: Operation,
        code: String,
    ) {
        operation.fail(code)
        operationRepository.save(operation)
    }

    /**
     * 메모리의 [Operation]이 이미 바뀌어 있어도(커밋이 실패해 되돌려진 경우) 저장된 값을 기준으로 실패로 끝낸다.
     * 진행 중이 아니거나 없으면 아무것도 하지 않는다.
     */
    @Transactional
    fun failRunning(
        operationId: String,
        code: String,
    ) {
        val operation = operationRepository.findById(operationId).orElse(null) ?: return
        if (operation.status != OperationStatus.RUNNING) return
        fail(operation, code)
    }
}
