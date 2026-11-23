package com.gsmsv.api.database.service

import com.gsmsv.api.database.config.DatabaseProperties
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseIsolation
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.global.authorization.Owner
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 개인 DB 한도와 사용량. 다른 도메인은 DB repository 대신 이 service로 받는다 */
@Service
class DatabaseQuotaService(
    private val repository: DatabaseRepository,
    private val usageReader: DatabaseUsageReader,
    private val properties: DatabaseProperties,
) {
    @Transactional(readOnly = true)
    fun personalUsage(userId: String): DatabaseQuotaUsage =
        DatabaseQuotaUsage(
            countUsed = repository.countByOwnerTypeAndOwnerIdAndDeletedAtIsNull(Database.OwnerType.USER, userId),
            countLimit = properties.countLimit.personal.toLong(),
            sharedStorageUsedGib = usageReader.ownerUsedGib(Owner.User(userId)),
            sharedStorageLimitGib = properties.storageLimit.personalGib,
        )

    @Transactional(readOnly = true)
    fun teamUsage(teamId: String): TeamDatabaseQuotaUsage {
        val owner = Owner.Team(teamId)
        val sharedStorageUsedGib = usageReader.ownerUsedGib(owner)
        val sharedStorageLimitGib = properties.storageLimit.teamGib
        return TeamDatabaseQuotaUsage(
            countUsed = repository.countByOwnerTypeAndOwnerIdAndDeletedAtIsNull(Database.OwnerType.TEAM, teamId),
            countLimit = properties.countLimit.team.toLong(),
            dedicatedUsed =
                repository.countByOwnerTypeAndOwnerIdAndIsolationAndDeletedAtIsNull(
                    Database.OwnerType.TEAM,
                    teamId,
                    DatabaseIsolation.DEDICATED,
                ),
            dedicatedLimit = properties.dedicatedLimit.team.toLong(),
            sharedStorageUsedGib = sharedStorageUsedGib,
            sharedStorageLimitGib = sharedStorageLimitGib,
            writeLocked = sharedStorageUsedGib > sharedStorageLimitGib,
        )
    }
}

data class DatabaseQuotaUsage(
    val countUsed: Long,
    val countLimit: Long,
    val sharedStorageUsedGib: Double,
    val sharedStorageLimitGib: Double,
)

data class TeamDatabaseQuotaUsage(
    val countUsed: Long,
    val countLimit: Long,
    val dedicatedUsed: Long,
    val dedicatedLimit: Long,
    val sharedStorageUsedGib: Double,
    val sharedStorageLimitGib: Double,
    val writeLocked: Boolean,
)
