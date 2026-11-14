package com.gsmsv.api.operation

import com.gsmsv.api.TestcontainersConfiguration
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.repository.DatabaseQuotaLock
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.ResourceType
import com.gsmsv.api.operation.domain.OperationStatus
import com.gsmsv.api.operation.repository.OperationRepository
import com.gsmsv.api.operation.service.OperationService
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.repository.UserRepository
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="])
@Tag("integration")
@Transactional
class OperationPersistenceTest
    @Autowired
    constructor(
        private val operationService: OperationService,
        private val operationRepository: OperationRepository,
        private val databaseRepository: DatabaseRepository,
        private val quotaLock: DatabaseQuotaLock,
        private val userRepository: UserRepository,
    ) {
        @Test
        fun `작업을 저장하면 진행 중으로 꺼내고 끝내면 종료 시각이 남는다`() {
            val userId = saveUser()
            val operation = operationService.register("db.create", "db", "db-k3m9x2p7q4ta", userId)
            operationRepository.flush()

            val running = operationRepository.findById(operation.id).orElseThrow()
            assertEquals(OperationStatus.RUNNING, running.status)
            assertEquals("running", running.stage)
            assertNull(running.finishedAt)

            operationService.succeed(running)
            operationRepository.flush()

            val finished = operationRepository.findById(operation.id).orElseThrow()
            assertEquals(OperationStatus.SUCCEEDED, finished.status)
            assertNotNull(finished.finishedAt)
        }

        @Test
        fun `실패한 작업은 오류 code만 남긴다`() {
            val userId = saveUser()
            val operation = operationService.register("db.create", "db", "db-k3m9x2p7q4ta", userId)

            operationService.fail(operation, "UPSTREAM_UNAVAILABLE")
            operationRepository.flush()

            val failed = operationRepository.findById(operation.id).orElseThrow()
            assertEquals(OperationStatus.FAILED, failed.status)
            assertEquals("UPSTREAM_UNAVAILABLE", failed.errorCode)
        }

        @Test
        fun `쓰기가 막힌 DB가 있는 소유자만 쓰기 금지로 센다`() {
            val userId = saveUser()
            val other = saveUser()
            val locked = saveDatabase(userId)
            saveDatabase(other)
            assertFalse(databaseRepository.existsByOwnerTypeAndOwnerIdAndWriteLockedTrueAndDeletedAtIsNull(Database.OwnerType.USER, userId))

            locked.lockWrites()
            databaseRepository.saveAndFlush(locked)

            assertTrue(databaseRepository.existsByOwnerTypeAndOwnerIdAndWriteLockedTrueAndDeletedAtIsNull(Database.OwnerType.USER, userId))
            assertFalse(databaseRepository.existsByOwnerTypeAndOwnerIdAndWriteLockedTrueAndDeletedAtIsNull(Database.OwnerType.USER, other))
        }

        @Test
        fun `소유자 잠금은 같은 트랜잭션에서 여러 번 잡아도 막히지 않는다`() {
            val userId = saveUser()

            quotaLock.lock(Database.OwnerType.USER, userId)
            quotaLock.lock(Database.OwnerType.USER, userId)
        }

        private fun saveUser(): String {
            val id = IdGenerator.userId()
            userRepository.saveAndFlush(User(id = id, dataGsmId = "gsm-$id", email = "$id@example.com", name = "테스트 사용자"))
            return id
        }

        private fun saveDatabase(ownerId: String): Database =
            databaseRepository.saveAndFlush(
                Database.create(
                    id = IdGenerator.resourceId(ResourceType.DATABASE),
                    name = "통합 테스트 DB",
                    owner = Owner.User(ownerId),
                    engine = DatabaseEngine.MYSQL,
                    createdBy = ownerId,
                    expiresAt = Instant.now().plusSeconds(14 * 24 * 3600),
                ),
            )
    }
