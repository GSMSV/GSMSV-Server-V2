package com.gsmsv.api.database.service

import com.gsmsv.api.database.crypto.DatabasePasswordCipher
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseStatus
import com.gsmsv.api.database.provisioner.SharedDatabaseProvisioner
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.global.grn.GrnParser
import com.gsmsv.api.notification.domain.NotificationCategory
import com.gsmsv.api.notification.service.NotificationMessage
import com.gsmsv.api.notification.service.NotificationResource
import com.gsmsv.api.notification.service.NotificationSender
import com.gsmsv.api.operation.domain.Operation
import com.gsmsv.api.operation.service.OperationService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

private val log = KotlinLogging.logger {}

/**
 * 요청이 끝난 뒤 뒤에서 공유 DB 서버에 database와 계정을 만들고 결과를 남긴다.
 *
 * 권한 판단은 요청에서 끝났으므로 여기서 하지 않는다. 요청이 만든 [Database]를 그대로 받아 id로 다시 찾지 않는다 (22번 규칙).
 * 성공하면 암호화한 비밀번호를 저장하고 `active`로, 실패하면 `failed`로 바꾸고 알림을 보낸다.
 * 암호화 키와 공유 DB 서버 연결은 설정에 따라 빈이 없을 수 있어, 없으면 기동이 아니라 작업이 실패로 끝난다.
 *
 * 공유 DB 서버를 기다리는 동안 앱 DB 커넥션을 잡지 않도록 이 클래스는 트랜잭션 밖에서 돈다.
 * 결과는 짧은 트랜잭션 하나로 기록하고, 알림은 그 트랜잭션이 끝난 뒤 보내 알림 실패가 결과를 되돌리지 못하게 한다.
 */
@Service
class DatabaseProvisionJob(
    private val databaseRepository: DatabaseRepository,
    private val operationService: OperationService,
    private val cipherProvider: ObjectProvider<DatabasePasswordCipher>,
    private val provisionerProvider: ObjectProvider<SharedDatabaseProvisioner>,
    private val notificationSender: NotificationSender,
    private val transactionTemplate: TransactionTemplate,
) {
    fun run(
        database: Database,
        operation: Operation,
        password: String,
    ) {
        try {
            val provisioner = checkNotNull(provisionerProvider.ifAvailable) { "공유 DB 서버 연결이 설정되지 않았습니다." }
            val cipher = checkNotNull(cipherProvider.ifAvailable) { "DB 비밀번호 암호화 키가 설정되지 않았습니다." }
            val encrypted = cipher.encrypt(password)
            provisioner.create(database.id, password, database.maxConnections)
            try {
                transactionTemplate.executeWithoutResult { recordSuccess(database, operation, encrypted) }
            } catch (e: Exception) {
                // 결과를 못 남겼으면 만들어 둔 database와 계정을 남기지 않는다. 고아로 남으면 아무도 지우지 못한다
                dropQuietly(provisioner, database.id)
                throw e
            }
        } catch (e: Exception) {
            // 로그에는 원인 종류만 남긴다. 접속 주소나 비밀번호가 메시지에 섞일 수 있다
            log.error { "DB 만들기에 실패했습니다. db=${database.id}, operation=${operation.id}, cause=${e.javaClass.simpleName}" }
            recordFailure(database, operation)
            notify(database, NotificationCategory.RESOURCE_FAILED, "DB를 만들지 못했습니다", "잠시 뒤 다시 만들어 주세요.")
            return
        }
        notify(database, NotificationCategory.RESOURCE_CREATED, "DB를 만들었습니다", "접속 정보를 확인하고 바로 사용할 수 있습니다.")
    }

    private fun recordSuccess(
        database: Database,
        operation: Operation,
        encryptedPassword: String,
    ) {
        database.changePassword(encryptedPassword)
        database.markActive()
        databaseRepository.save(database)
        operationService.succeed(operation)
    }

    /**
     * 성공 기록의 커밋이 실패했으면 메모리의 [database]는 이미 `active`로 바뀐 채 저장은 되돌려진 상태다.
     * 그때는 상태 검사에 걸려 실패 기록도 못 하므로, 아직 `creating`일 때만 바꾸고 아니면 복구가 필요하다고 남긴다.
     * 작업은 저장된 값을 기준으로 끝낸다.
     */
    private fun recordFailure(
        database: Database,
        operation: Operation,
    ) {
        try {
            transactionTemplate.executeWithoutResult {
                if (database.status == DatabaseStatus.CREATING) {
                    database.markFailed()
                    databaseRepository.save(database)
                } else {
                    log.error { "DB 상태를 실패로 남기지 못했습니다. 복구가 필요합니다. db=${database.id}" }
                }
                operationService.failRunning(operation.id, FAILURE_CODE)
            }
        } catch (e: Exception) {
            log.error { "DB 만들기 실패를 기록하지 못했습니다. 복구가 필요합니다. db=${database.id}, cause=${e.javaClass.simpleName}" }
        }
    }

    private fun dropQuietly(
        provisioner: SharedDatabaseProvisioner,
        databaseId: String,
    ) {
        try {
            provisioner.drop(databaseId)
        } catch (e: Exception) {
            log.error { "만들다 만 database를 정리하지 못했습니다. 직접 정리가 필요합니다. db=$databaseId, cause=${e.javaClass.simpleName}" }
        }
    }

    private fun notify(
        database: Database,
        category: NotificationCategory,
        title: String,
        body: String,
    ) {
        try {
            val grn = GrnParser.format(database.owner.id, database.id)
            notificationSender.send(database.owner, NotificationMessage(category, title, body, NotificationResource(grn, database.name)))
        } catch (e: Exception) {
            // 알림이 실패해도 작업 결과는 바꾸지 않는다. 트랜잭션 밖이라 결과를 되돌리지도 않는다
            log.warn { "DB 만들기 알림을 보내지 못했습니다. db=${database.id}, cause=${e.javaClass.simpleName}" }
        }
    }

    private companion object {
        const val FAILURE_CODE = "UPSTREAM_UNAVAILABLE"
    }
}
