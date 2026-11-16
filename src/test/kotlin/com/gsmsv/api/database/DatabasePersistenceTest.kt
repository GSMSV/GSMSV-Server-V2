package com.gsmsv.api.database

import com.gsmsv.api.TestcontainersConfiguration
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.database.service.DatabaseResourceReader
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.ResourceType
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="])
@Tag("integration")
@Transactional
class DatabasePersistenceTest
    @Autowired
    constructor(
        private val databaseRepository: DatabaseRepository,
        private val databaseReader: DatabaseResourceReader,
        private val userRepository: UserRepository,
    ) {
        @Test
        fun `저장한 DB를 id로 다시 꺼내고 소유자를 그대로 돌려준다`() {
            val creatorId = saveUser()
            val database = saveDatabase(Owner.User(creatorId), creatorId)

            val found = databaseReader.findById(database.id)

            assertNotNull(found)
            assertEquals(Owner.User(creatorId), found.owner)
            assertEquals(ResourceType.DATABASE, databaseReader.type)
        }

        @Test
        fun `삭제 표시가 된 DB는 조회와 목록에서 빠진다`() {
            val creatorId = saveUser()
            val database = saveDatabase(Owner.User(creatorId), creatorId)
            database.markActive()
            database.startDeleting()
            database.markDeleted()
            databaseRepository.saveAndFlush(database)

            assertNull(databaseReader.findById(database.id))
            assertTrue(databaseReader.findAllByOwners(listOf(Owner.User(creatorId))).isEmpty())
            // 행은 지워지지 않고 남아 있다
            assertNotNull(databaseRepository.findById(database.id).orElse(null))
        }

        @Test
        fun `소유자 목록으로 개인 DB와 팀 DB를 함께 거른다`() {
            val creatorId = saveUser()
            val teamId = IdGenerator.teamId()
            val mine = saveDatabase(Owner.User(creatorId), creatorId)
            val teams = saveDatabase(Owner.Team(teamId), creatorId)
            saveDatabase(Owner.Team(IdGenerator.teamId()), creatorId)

            val ids = databaseReader.findAllByOwners(listOf(Owner.User(creatorId), Owner.Team(teamId))).map { it.id }

            assertEquals(setOf(mine.id, teams.id), ids.toSet())
        }

        private fun saveUser(): String {
            val id = IdGenerator.userId()
            userRepository.saveAndFlush(
                User(id = id, dataGsmId = "gsm-$id", email = "$id@example.com", name = "테스트 사용자"),
            )
            return id
        }

        private fun saveDatabase(
            owner: Owner,
            createdBy: String,
        ): Database =
            databaseRepository.saveAndFlush(
                Database.create(
                    id = IdGenerator.resourceId(ResourceType.DATABASE),
                    name = "통합 테스트 DB",
                    owner = owner,
                    engine = DatabaseEngine.MYSQL,
                    createdBy = createdBy,
                    expiresAt = Instant.now().plusSeconds(14 * 24 * 3600),
                ),
            )
    }
