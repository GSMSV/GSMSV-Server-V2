package com.gsmsv.api.database.service

import com.gsmsv.api.audit.domain.AuditEventType
import com.gsmsv.api.audit.service.AuditRecorder
import com.gsmsv.api.database.config.DatabaseProperties
import com.gsmsv.api.database.crypto.DatabasePasswordCipher
import com.gsmsv.api.database.crypto.DatabasePasswordGenerator
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.domain.DatabaseIsolation
import com.gsmsv.api.database.domain.DatabaseStatus
import com.gsmsv.api.database.dto.DatabaseCredentialResponse
import com.gsmsv.api.database.dto.PasswordRotationResponse
import com.gsmsv.api.database.dto.ReconnectedResponse
import com.gsmsv.api.database.provisioner.SharedDatabaseProvisioner
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.global.authorization.Actor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.GrnParser
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * DB 접속 정보 조회(7.5)와 비밀번호 교체(7.6). 로더가 돌려준 [Database]를 받으므로 권한 판단은 끝난 상태다.
 *
 * 두 동작 모두 감사 로그를 남긴다. 감사 로그에는 대상 GRN만 넣고 비밀번호 값은 넣지 않는다.
 * 암호화 키와 공유 DB 서버 연결은 설정에 따라 빈이 없을 수 있어, 없으면 기동이 아니라 호출할 때 실패한다.
 */
@Service
class DatabaseCredentialService(
    private val databaseRepository: DatabaseRepository,
    private val cipherProvider: ObjectProvider<DatabasePasswordCipher>,
    private val provisionerProvider: ObjectProvider<SharedDatabaseProvisioner>,
    private val auditRecorder: AuditRecorder,
    private val properties: DatabaseProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** 볼 때마다 감사 로그를 남긴다. 기록이 실패하면 비밀번호를 내주지 않는다 */
    @Transactional
    fun credentials(
        actor: Actor,
        database: Database,
    ): DatabaseCredentialResponse {
        // 계정을 아직 만들지 않았으면 보여 줄 비밀번호가 없다
        val encrypted = database.passwordEncrypted ?: throw BusinessException(ErrorCode.CONFLICT)
        val password = cipher().decrypt(encrypted)
        auditRecorder.record(actor, AuditEventType.DATABASE_CREDENTIALS_READ, grnOf(database))
        val connection = properties.connection
        return DatabaseCredentialResponse(
            host = connection.host,
            port = connection.port,
            // 서버 쪽 database·계정 이름은 db id 그대로다 (사용자가 지은 이름은 쓰지 않는다)
            database = database.id,
            username = database.id,
            password = password,
            jdbcUrl = jdbcUrl(database),
        )
    }

    /**
     * 새 무작위 비밀번호를 적용하고 그 계정의 기존 연결을 끊는다. 동기로 처리한다.
     *
     * 공유 DB 서버는 이 트랜잭션에 묶이지 않으므로 DB 쪽 저장·감사 기록을 먼저 하고 서버 교체를 마지막에 한다.
     * 서버 교체가 실패하면 예외로 트랜잭션이 되돌아가 저장한 비밀번호와 서버 비밀번호가 어긋나지 않는다.
     */
    @Transactional
    fun rotate(
        actor: Actor,
        database: Database,
    ): PasswordRotationResponse {
        // 전용형의 교체 대상·관리자 계정은 아직 정하지 않았다. 1차는 공유형만 한다
        if (database.isolation == DatabaseIsolation.DEDICATED) {
            throw BusinessException(ErrorCode.NOT_SUPPORTED_FOR_DEDICATED)
        }
        if (database.status !in ROTATABLE) throw BusinessException(ErrorCode.CONFLICT)

        val newPassword = DatabasePasswordGenerator.generate()
        database.changePassword(cipher().encrypt(newPassword))
        databaseRepository.save(database)
        auditRecorder.record(actor, AuditEventType.DATABASE_PASSWORD_ROTATE, grnOf(database))
        provisioner().rotatePassword(database.id, newPassword)
        return PasswordRotationResponse(
            password = newPassword,
            rotatedAt = Instant.now(clock),
            // 서버리스 함수·원터치 배포 앱 도메인이 생기면 그 접속 정보를 갱신하고 개수를 센다
            reconnected = ReconnectedResponse(functions = 0, appCells = 0),
        )
    }

    private fun jdbcUrl(database: Database): String {
        val connection = properties.connection
        val (scheme, tlsOption) =
            when (database.engine) {
                DatabaseEngine.MYSQL -> "mysql" to "sslMode=REQUIRED"
                DatabaseEngine.POSTGRESQL -> "postgresql" to "sslmode=require"
            }
        val base = "jdbc:$scheme://${connection.host}:${connection.port}/${database.id}"
        return if (connection.tls) "$base?$tlsOption" else base
    }

    private fun grnOf(database: Database) = GrnParser.format(database.owner.id, database.id)

    private fun cipher(): DatabasePasswordCipher =
        checkNotNull(cipherProvider.ifAvailable) { "DB 비밀번호 암호화 키(gsmsv.database.encryption-key)가 설정되지 않았습니다." }

    private fun provisioner(): SharedDatabaseProvisioner = checkNotNull(provisionerProvider.ifAvailable) { "공유 DB 서버 연결이 설정되지 않았습니다." }

    private companion object {
        // 정지된 DB도 계정은 남아 있으므로 바꿀 수 있다. 만드는 중·삭제 중·실패한 DB는 바꿀 계정이 확실하지 않다
        val ROTATABLE = setOf(DatabaseStatus.ACTIVE, DatabaseStatus.SUSPENDED)
    }
}
