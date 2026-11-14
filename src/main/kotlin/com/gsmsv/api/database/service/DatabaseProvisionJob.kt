package com.gsmsv.api.database.service

import com.gsmsv.api.database.crypto.DatabasePasswordCipher
import com.gsmsv.api.database.domain.Database
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
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

/**
 * 요청이 끝난 뒤 뒤에서 공유 DB 서버에 database와 계정을 만들고 결과를 남긴다.
 *
 * 권한 판단은 요청에서 끝났으므로 여기서 하지 않는다. 요청이 만든 [Database]를 그대로 받아 id로 다시 찾지 않는다.
 * 성공하면 암호화한 비밀번호를 저장하고 `active`로, 실패하면 `failed`로 바꾸고 알림을 보낸다.
 * 암호화 키와 공유 DB 서버 연결은 설정에 따라 빈이 없을 수 있어, 없으면 기동이 아니라 작업이 실패로 끝난다.
 */
@Service
class DatabaseProvisionJob(
    private val databaseRepository: DatabaseRepository,
    private val operationService: OperationService,
    private val cipherProvider: ObjectProvider<DatabasePasswordCipher>,
    private val provisionerProvider: ObjectProvider<SharedDatabaseProvisioner>,
    private val notificationSender: NotificationSender,
) {
    @Transactional
    fun run(
        database: Database,
        operation: Operation,
        password: String,
    ) {
        try {
            val provisioner = checkNotNull(provisionerProvider.ifAvailable) { "공유 DB 서버 연결이 설정되지 않았습니다." }
            val cipher = checkNotNull(cipherProvider.ifAvailable) { "DB 비밀번호 암호화 키가 설정되지 않았습니다." }
            provisioner.create(database.id, password, database.maxConnections)
            database.changePassword(cipher.encrypt(password))
            database.markActive()
            databaseRepository.save(database)
            operationService.succeed(operation)
            notify(database, NotificationCategory.RESOURCE_CREATED, "DB를 만들었습니다", "접속 정보를 확인하고 바로 사용할 수 있습니다.")
        } catch (e: Exception) {
            // 로그에는 원인 종류만 남긴다. 접속 주소나 비밀번호가 메시지에 섞일 수 있다
            log.error { "DB 만들기에 실패했습니다. db=${database.id}, operation=${operation.id}, cause=${e.javaClass.simpleName}" }
            database.markFailed()
            databaseRepository.save(database)
            operationService.fail(operation, FAILURE_CODE)
            notify(database, NotificationCategory.RESOURCE_FAILED, "DB를 만들지 못했습니다", "잠시 뒤 다시 만들어 주세요.")
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
            // 알림이 실패해도 작업 결과는 바꾸지 않는다
            log.warn { "DB 만들기 알림을 보내지 못했습니다. db=${database.id}, cause=${e.javaClass.simpleName}" }
        }
    }

    private companion object {
        const val FAILURE_CODE = "UPSTREAM_UNAVAILABLE"
    }
}
