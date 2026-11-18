package com.gsmsv.api.operation.repository

import com.gsmsv.api.operation.domain.Operation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * 요청에서 쓰는 id 조회는 [com.gsmsv.api.operation.service.OperationResourceReader] 밖에서 부르지 않는다 (22번 규칙).
 * 폴링·SSE·정리처럼 이미 권한을 지난 operation이나 내부 작업만 다루는 코드는 여기서 직접 읽는다.
 */
interface OperationRepository : JpaRepository<Operation, String> {
    /** Infra에 맡겼고 아직 끝나지 않은 작업 */
    fun findAllByFinishedAtIsNullAndJobIdIsNotNull(): List<Operation>

    @Modifying
    @Query("delete from Operation o where o.finishedAt < :before")
    fun deleteFinishedBefore(
        @Param("before") before: Instant,
    ): Int
}
