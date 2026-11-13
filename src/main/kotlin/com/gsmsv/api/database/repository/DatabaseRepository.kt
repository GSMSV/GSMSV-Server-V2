package com.gsmsv.api.database.repository

import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseStatus
import org.springframework.data.jpa.repository.JpaRepository

/** id 조회는 [com.gsmsv.api.database.service.DatabaseResourceReader] 밖에서 부르지 않는다 (22번 규칙). */
interface DatabaseRepository : JpaRepository<Database, String> {
    fun findByIdAndDeletedAtIsNull(id: String): Database?

    fun findAllByDeletedAtIsNull(): List<Database>

    /** 백업 작업이 대상 DB를 고를 때만 쓴다. id로 찾는 길이 아니다 */
    fun findAllByStatusIn(statuses: Collection<DatabaseStatus>): List<Database>

    /** 한도 사용량을 센다. 삭제가 끝나기 전(삭제 중·정지 유예)의 DB도 센다 (Contracts-V2 policies/quota.md 1장) */
    fun countByOwnerTypeAndOwnerIdAndDeletedAtIsNull(
        ownerType: Database.OwnerType,
        ownerId: String,
    ): Long

    fun findAllByOwnerTypeAndOwnerIdInAndDeletedAtIsNull(
        ownerType: Database.OwnerType,
        ownerIds: Collection<String>,
    ): List<Database>
}
