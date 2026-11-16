package com.gsmsv.api.database.service

import com.gsmsv.api.database.config.DatabaseProperties
import com.gsmsv.api.database.crypto.DatabasePasswordGenerator
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.dto.CreateDatabaseRequest
import com.gsmsv.api.database.dto.CreateDatabaseResponse
import com.gsmsv.api.database.dto.CreatedDatabaseResourceResponse
import com.gsmsv.api.database.repository.DatabaseQuotaLock
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.GrnParser
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.ResourceType
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.operation.dto.OperationRefResponse
import com.gsmsv.api.operation.service.OperationExecutor
import com.gsmsv.api.operation.service.OperationService
import com.gsmsv.api.team.domain.Team
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * 개인 DB(7.2)와 팀 DB(7.3) 발급. 권한 판단은 끝난 상태로 부른다 (개인은 본인, 팀은 `TeamLoader`가 돌려준 [Team]).
 *
 * 요청 안에서는 한도·쓰기 금지를 검사하고 `creating` DB와 operation을 저장해 202로 답한다.
 * 공유 DB 서버에 실제로 만드는 일은 커밋된 뒤 [DatabaseProvisionJob]이 이어서 한다.
 *
 * **(미정)** 첫 사용 기간은 계약에 없다. VM 사용 기간 규칙(24번 DBaaS)에 따라 앱과 같게 개인 14일, 팀 31일로 둔다.
 */
@Service
class DatabaseProvisionService(
    private val databaseRepository: DatabaseRepository,
    private val quotaLock: DatabaseQuotaLock,
    private val operationService: OperationService,
    private val job: DatabaseProvisionJob,
    private val executor: OperationExecutor,
    private val properties: DatabaseProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun createPersonal(
        user: AuthenticatedUser,
        request: CreateDatabaseRequest,
    ): CreateDatabaseResponse {
        val lifetime = if (user.role == Role.ADMIN) TEAM_LIFETIME else PERSONAL_LIFETIME
        return create(user, Owner.User(user.userId), request, properties.countLimit.personal, lifetime)
    }

    @Transactional
    fun createTeam(
        user: AuthenticatedUser,
        team: Team,
        request: CreateDatabaseRequest,
    ): CreateDatabaseResponse = create(user, Owner.Team(team.id), request, properties.countLimit.team, TEAM_LIFETIME)

    private fun create(
        user: AuthenticatedUser,
        owner: Owner,
        request: CreateDatabaseRequest,
        countLimit: Int,
        lifetime: Duration,
    ): CreateDatabaseResponse {
        // 길이와 엔진은 요청 검증(Bean Validation)이 끝냈다
        val name = request.name.trim()
        val engine = DatabaseEngine.MYSQL
        val ownerType = if (owner is Owner.User) Database.OwnerType.USER else Database.OwnerType.TEAM

        quotaLock.lock(ownerType, owner.id)
        if (databaseRepository.existsByOwnerTypeAndOwnerIdAndWriteLockedTrueAndDeletedAtIsNull(ownerType, owner.id)) {
            throw BusinessException(ErrorCode.WRITE_LOCKED_OWNER)
        }
        val used = databaseRepository.countByOwnerTypeAndOwnerIdAndDeletedAtIsNull(ownerType, owner.id)
        if (used >= countLimit) {
            throw BusinessException(ErrorCode.QUOTA_EXCEEDED, details = mapOf("limit" to countLimit, "used" to used))
        }

        val id = IdGenerator.resourceId(ResourceType.DATABASE)
        val database = databaseRepository.save(Database.create(id, name, owner, engine, user.userId, Instant.now(clock).plus(lifetime)))
        val operation = operationService.register(OPERATION_TYPE, RESOURCE_TYPE, database.id, user.userId)
        // 비밀번호는 저장하지 않고 이 요청에서 만든 값을 작업에 넘긴다. 계정을 만든 뒤에만 암호화해 저장한다
        val password = DatabasePasswordGenerator.generate()
        runAfterCommit { job.run(database, operation, password) }

        return CreateDatabaseResponse(
            operation = OperationRefResponse.from(operation),
            resource = CreatedDatabaseResourceResponse(GrnParser.format(owner.id, database.id), database.status.name.lowercase()),
        )
    }

    /** 커밋되기 전에 일을 시작하면 아직 저장되지 않은 DB를 다루게 되므로 커밋된 뒤에 보낸다 */
    private fun runAfterCommit(task: Runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            executor.execute(task)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    executor.execute(task)
                }
            },
        )
    }

    private companion object {
        const val OPERATION_TYPE = "db.create"
        const val RESOURCE_TYPE = "db"
        val PERSONAL_LIFETIME: Duration = Duration.ofDays(14)
        val TEAM_LIFETIME: Duration = Duration.ofDays(31)
    }
}
