package com.gsmsv.api.database.service

import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseIsolation
import com.gsmsv.api.database.domain.DatabaseStatus
import com.gsmsv.api.database.dto.DatabaseExtendResponse
import com.gsmsv.api.database.dto.RenameDatabaseResponse
import com.gsmsv.api.database.provisioner.SharedDatabaseProvisioner
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.GrnParser
import com.gsmsv.api.global.response.FieldErrorDetail
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * DB 이름 수정(7.7)과 사용 기간 연장(7.9). 로더가 돌려준 [Database]를 받으므로 권한 판단은 끝난 상태다.
 *
 * 이름은 사용자에게 보이는 값일 뿐이다. 서버 쪽 database·계정 이름은 db id라서 이름을 바꿔도 공유 DB 서버는 건드리지 않는다.
 */
@Service
class DatabaseManageService(
    private val databaseRepository: DatabaseRepository,
    private val provisionerProvider: ObjectProvider<SharedDatabaseProvisioner>,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun rename(
        database: Database,
        rawName: String,
    ): RenameDatabaseResponse {
        val name = rawName.trim()
        if (name.codePointCount(0, name.length) !in NAME_LENGTH) {
            throw BusinessException(
                ErrorCode.VALIDATION_FAILED,
                details = mapOf("fields" to listOf(FieldErrorDetail("name", "DB 이름은 공백 제거 후 1~30자여야 합니다."))),
            )
        }
        // 지우는 중인 DB의 이름을 바꿔도 의미가 없다
        if (database.status == DatabaseStatus.DELETING) throw BusinessException(ErrorCode.CONFLICT)
        database.rename(name)
        databaseRepository.save(database)
        return RenameDatabaseResponse(GrnParser.format(database.owner.id, database.id), database.name)
    }

    /**
     * 만료 7일 전부터 14일씩 늘린다. 기준점은 "지금"과 기존 만료일 중 늦은 쪽이라 유예 중 연장해도 만료일 뒤로만 늘어난다.
     *
     * 공유 DB 서버는 이 트랜잭션에 묶이지 않으므로 저장을 먼저 하고 계정 잠금 해제를 마지막에 한다.
     * 해제가 실패하면 예외로 트랜잭션이 되돌아가 만료일만 늘고 계정은 잠긴 채로 남지 않는다.
     *
     * **(미정)** 연장을 신청할 수 있는 시점(7일)과 연장 길이(14일)는 계약의 VM 기간 규칙을 따랐다.
     */
    @Transactional
    fun extend(database: Database): DatabaseExtendResponse {
        // 전용형의 연장·잠금 해제 방식은 아직 정하지 않았다. 1차는 공유형만 한다
        if (database.isolation == DatabaseIsolation.DEDICATED) throw BusinessException(ErrorCode.NOT_SUPPORTED_FOR_DEDICATED)
        if (database.status !in EXTENDABLE) throw BusinessException(ErrorCode.CONFLICT)

        val now = Instant.now(clock)
        if (now.isBefore(database.expiresAt.minus(EXTEND_WINDOW))) throw BusinessException(ErrorCode.EXTENSION_NOT_YET_AVAILABLE)

        val wasSuspended = database.status == DatabaseStatus.SUSPENDED
        val base = if (now.isAfter(database.expiresAt)) now else database.expiresAt
        database.extend(base.plus(EXTEND_LENGTH))
        databaseRepository.save(database)
        if (wasSuspended) {
            checkNotNull(provisionerProvider.ifAvailable) { "공유 DB 서버 연결이 설정되지 않았습니다." }.unlock(database.id)
        }
        return DatabaseExtendResponse(database.expiresAt)
    }

    private companion object {
        val NAME_LENGTH = 1..30
        val EXTEND_WINDOW: Duration = Duration.ofDays(7)
        val EXTEND_LENGTH: Duration = Duration.ofDays(14)

        // 정지 유예 중에도 연장할 수 있다. 만드는 중·삭제 중·실패한 DB는 늘릴 이유가 없다
        val EXTENDABLE = setOf(DatabaseStatus.ACTIVE, DatabaseStatus.SUSPENDED)
    }
}
