package com.gsmsv.api.database.repository

import com.gsmsv.api.database.domain.Database
import org.springframework.data.jpa.repository.JpaRepository

/** id 조회는 [com.gsmsv.api.database.service.DatabaseResourceReader] 밖에서 부르지 않는다 (22번 규칙). */
interface DatabaseRepository : JpaRepository<Database, String> {
    fun findByIdAndDeletedAtIsNull(id: String): Database?

    fun findAllByDeletedAtIsNull(): List<Database>

    fun findAllByOwnerTypeAndOwnerIdInAndDeletedAtIsNull(
        ownerType: Database.OwnerType,
        ownerIds: Collection<String>,
    ): List<Database>
}
