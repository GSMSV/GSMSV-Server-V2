package com.gsmsv.api.database.service

import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceReader
import com.gsmsv.api.global.grn.ResourceType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * DB 자원 조회 포트. 삭제 표시가 된 DB는 돌려주지 않는다 (일반 사용자에게는 없는 자원이다).
 * 삭제된 DB를 되살리는 관리자 기능은 별도 조회를 쓴다.
 */
@Component
class DatabaseResourceReader(
    private val databaseRepository: DatabaseRepository,
) : ServiceResourceReader<Database> {
    override val type: ResourceType = ResourceType.DATABASE

    @Transactional(readOnly = true)
    override fun findById(id: String): Database? = databaseRepository.findByIdAndDeletedAtIsNull(id)

    @Transactional(readOnly = true)
    override fun findAll(): List<Database> = databaseRepository.findAllByDeletedAtIsNull()

    @Transactional(readOnly = true)
    override fun findAllByOwners(owners: Collection<Owner>): List<Database> =
        listOf(
            Database.OwnerType.USER to owners.filterIsInstance<Owner.User>(),
            Database.OwnerType.TEAM to owners.filterIsInstance<Owner.Team>(),
        ).filter { (_, byType) -> byType.isNotEmpty() }
            .flatMap { (ownerType, byType) ->
                databaseRepository.findAllByOwnerTypeAndOwnerIdInAndDeletedAtIsNull(ownerType, byType.map { it.id })
            }
}
