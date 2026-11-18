package com.gsmsv.api.operation.service

import com.gsmsv.api.global.authorization.OperationReader
import com.gsmsv.api.operation.config.OperationProperties
import com.gsmsv.api.operation.domain.Operation
import com.gsmsv.api.operation.repository.OperationRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** operation 조회 포트. 보관 기간이 지난 기록은 정리되기 전이어도 돌려주지 않는다 (계약: 그 뒤에는 404). */
@Component
class OperationResourceReader(
    private val operationRepository: OperationRepository,
    private val properties: OperationProperties,
    private val clock: Clock = Clock.systemUTC(),
) : OperationReader<Operation> {
    @Transactional(readOnly = true)
    override fun findById(id: String): Operation? =
        operationRepository
            .findById(id)
            .orElse(null)
            ?.takeUnless { it.isExpired(clock.instant(), properties.retention) }
}
