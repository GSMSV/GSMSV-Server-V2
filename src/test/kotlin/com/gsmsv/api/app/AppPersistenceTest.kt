package com.gsmsv.api.app

import com.gsmsv.api.TestcontainersConfiguration
import com.gsmsv.api.app.domain.App
import com.gsmsv.api.app.domain.AppOwnerType
import com.gsmsv.api.app.repository.AppEnvironmentRepository
import com.gsmsv.api.app.repository.AppRepository
import com.gsmsv.api.app.repository.AppResourceReader
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.grn.GrnParser
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.ResourceType
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="])
@Tag("integration")
@Transactional
class AppPersistenceTest
    @Autowired
    constructor(
        private val appRepository: AppRepository,
        private val environmentRepository: AppEnvironmentRepository,
        private val reader: AppResourceReader,
        private val loader: ServiceResourceLoader,
        private val entityManager: EntityManager,
        private val jdbcTemplate: JdbcTemplate,
        private val transactionManager: PlatformTransactionManager,
    ) {
        @Test
        fun `앱과 기본 production 환경을 저장하고 GRN 로더로 조회한다`() {
            val app = appRepository.saveAndFlush(app(Owner.User(USER_ID), "  내 앱  "))
            entityManager.clear()

            val requestAttributes = RequestContextHolder.getRequestAttributes()
            RequestContextHolder.resetRequestAttributes()
            val loaded =
                try {
                    loader.load(UserActor(USER_ID), GrnParser.format(USER_ID, app.id), Action.VIEW, reader)
                } finally {
                    RequestContextHolder.setRequestAttributes(requestAttributes)
                }
            val environments = environmentRepository.findAllByAppId(app.id)

            assertEquals("내 앱", loaded.name)
            assertEquals(Owner.User(USER_ID), loaded.owner)
            assertEquals(USER_ID, loaded.createdByUserId)
            assertEquals("creating", loaded.status)
            assertEquals(1, environments.size)
            assertEquals("production", environments.single().name)
            assertTrue(environments.single().id.matches(Regex("^env-[0-9a-hjkmnp-tv-z]{8}$")))
            assertEquals(app.id, environments.single().parentId)
        }

        @Test
        fun `환경은 실제 앱에만 속하고 목록은 볼 수 있는 소유자로 거른다`() {
            val personalApp = appRepository.saveAndFlush(app(Owner.User(USER_ID), "같은 이름"))
            val teamApp = appRepository.saveAndFlush(app(Owner.Team(TEAM_ID), "같은 이름"))
            entityManager.clear()

            assertEquals(1, environmentRepository.countByAppId(personalApp.id))
            assertEquals(1, environmentRepository.countByAppId(teamApp.id))
            assertTrue(environmentRepository.findAllByAppId(personalApp.id).all { it.parentId == personalApp.id })
            assertTrue(environmentRepository.findAllByAppId(teamApp.id).all { it.parentId == teamApp.id })
            assertEquals(listOf(personalApp.id), loader.listVisible(UserActor(USER_ID), reader).map { it.id })
            assertNotNull(reader.findById(teamApp.id))
        }

        @Test
        fun `없는 앱을 부모로 한 환경은 DB가 거절한다`() {
            assertFailsWith<DataIntegrityViolationException> {
                jdbcTemplate.update(
                    "INSERT INTO app_environments (id, app_id, name) VALUES (?, ?, ?)",
                    "env-7qz3m8k2",
                    "app-7qz3m8k2x5ra",
                    "production",
                )
            }
        }

        @Test
        fun `같은 소유자의 앱은 같은 이름으로 두 개 저장할 수 있다`() {
            val first = appRepository.saveAndFlush(app(Owner.User(USER_ID), "같은 이름"))
            val second = appRepository.saveAndFlush(app(Owner.User(USER_ID), "같은 이름"))

            assertEquals(2, appRepository.countByOwnerTypeAndOwnerId(AppOwnerType.USER, USER_ID))
            assertEquals(1, environmentRepository.countByAppId(first.id))
            assertEquals(1, environmentRepository.countByAppId(second.id))
        }

        @Test
        fun `앱 저장 트랜잭션이 실패하면 기본 환경도 함께 롤백한다`() {
            val app = app(Owner.User(USER_ID), "롤백 확인")
            val transaction = TransactionTemplate(transactionManager)
            transaction.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW

            assertFailsWith<IllegalStateException> {
                transaction.executeWithoutResult {
                    appRepository.saveAndFlush(app)
                    assertEquals(1, environmentRepository.countByAppId(app.id))
                    throw IllegalStateException("롤백 확인")
                }
            }

            assertEquals(0, environmentRepository.countByAppId(app.id))
            assertTrue(appRepository.findById(app.id).isEmpty)
        }

        private fun app(
            owner: Owner,
            name: String,
        ) = App(IdGenerator.resourceId(ResourceType.APP), name, owner, USER_ID, Instant.parse("2026-12-01T00:00:00Z"))

        companion object {
            private const val USER_ID = "user-a1b2c3d4e5f6"
            private const val TEAM_ID = "team-4p8w2k9m1xza"
        }
    }
