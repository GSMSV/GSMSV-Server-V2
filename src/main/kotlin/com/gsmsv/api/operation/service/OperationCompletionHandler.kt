package com.gsmsv.api.operation.service

import com.gsmsv.api.operation.domain.Operation
import com.gsmsv.api.operation.domain.OperationType

/**
 * 작업이 끝났을 때 대상 자원의 상태를 맞추는 도메인 쪽 처리 (예: `vm.create`가 끝나면 VM을 `ACTIVE`·`FAILED`로).
 *
 * 각 도메인이 빈으로 등록한다. operation 상태를 바꾸는 트랜잭션 안에서 불리므로, 실패하면 operation 변경도 함께 되돌린다.
 */
interface OperationCompletionHandler {
    /** 이 처리가 맡는 작업 종류 */
    val types: Set<OperationType>

    fun onFinished(operation: Operation)
}
