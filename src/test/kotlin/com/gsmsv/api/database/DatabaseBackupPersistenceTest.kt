package com.gsmsv.api.database

import com.gsmsv.api.TestcontainersConfiguration
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseBackup
import com.gsmsv.api.database.domain.DatabaseBackupKind
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.repository.DatabaseBackupRepository
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.ResourceType
import com.gsmsv.api.global.grn.SubResourceType
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.repository.UserRepository
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="])
@Tag("integration")
@Transactional
class DatabaseBackupPersistenceTest
    @Autowired
    constructor(
        private val databaseRepository: DatabaseRepository,
        private val backupRepository: DatabaseBackupRepository,
        private val userRepository: UserRepository,
    ) {
        private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        @Test
        fun `기간이 지난 백업만 정리 대상으로 고른다`() {
            val database = saveDatabase()
            val expired = saveBackup(database.id, expiresAt = now.minusSeconds(60))
            saveBackup(database.id, expiresAt = now.plusSeconds(60))

            val found = backupRepository.findExpired(now, now.minus(7, ChronoUnit.DAYS))

            assertEquals(listOf(expired.id), found.map { it.id })
        }

        @Test
        fun `삭제된 DB의 백업은 삭제 뒤 보관 기간까지 정리하지 않는다`() {
            val database = saveDatabase()
            val backup = saveBackup(database.id, expiresAt = now.minusSeconds(60))
            database.startDeleting()
            database.markDeleted(now.minus(1, ChronoUnit.DAYS))
            databaseRepository.saveAndFlush(database)

            assertEquals(emptyList(), backupRepository.findExpired(now, now.minus(7, ChronoUnit.DAYS)))

            // 삭제한 지 7일이 지나면 정리 대상이 된다
            assertEquals(
                listOf(backup.id),
                backupRepository.findExpired(now, now.plus(1, ChronoUnit.DAYS)).map { it.id },
            )
        }

        @Test
        fun `오늘 일일 백업이 있는지 알려 준다`() {
            val database = saveDatabase()
            saveBackup(database.id, expiresAt = now.plusSeconds(60))

            assertEquals(
                true,
                backupRepository.existsByDatabaseIdAndKindAndCreatedAtGreaterThanEqual(
                    database.id,
                    DatabaseBackupKind.DAILY,
                    now.minusSeconds(3600),
                ),
            )
            assertEquals(
                false,
                backupRepository.existsByDatabaseIdAndKindAndCreatedAtGreaterThanEqual(
                    database.id,
                    DatabaseBackupKind.PRE_RESTORE,
                    now.minusSeconds(3600),
                ),
            )
        }

        private fun saveDatabase(): Database {
            val userId = IdGenerator.userId()
            userRepository.saveAndFlush(
                User(id = userId, dataGsmId = "gsm-$userId", email = "$userId@example.com", name = "테스트 사용자"),
            )
            return databaseRepository.saveAndFlush(
                Database
                    .create(
                        id = IdGenerator.resourceId(ResourceType.DATABASE),
                        name = "통합 테스트 DB",
                        owner = Owner.User(userId),
                        engine = DatabaseEngine.MYSQL,
                        createdBy = userId,
                        expiresAt = now.plusSeconds(14 * 24 * 3600),
                    ).also { it.markActive() },
            )
        }

        private fun saveBackup(
            databaseId: String,
            expiresAt: Instant,
        ): DatabaseBackup =
            backupRepository.saveAndFlush(
                DatabaseBackup(
                    id = IdGenerator.subResourceId(SubResourceType.BACKUP),
                    databaseId = databaseId,
                    kind = DatabaseBackupKind.DAILY,
                    sizeBytes = 10,
                    storageKey = "k.sql.gz",
                    createdAt = now,
                    expiresAt = expiresAt,
                ),
            )
    }
