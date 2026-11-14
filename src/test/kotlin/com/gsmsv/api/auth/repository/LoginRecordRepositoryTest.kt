package com.gsmsv.api.auth.repository

import com.gsmsv.api.TestcontainersConfiguration
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.domain.UserStatus
import com.gsmsv.api.user.repository.UserRepository
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** `DELETE ... RETURNING`으로 한 번만 소모되는지 실제 PostgreSQL로 확인한다 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="])
@Tag("integration")
@Transactional
class LoginRecordRepositoryTest
    @Autowired
    constructor(
        private val repository: LoginRecordRepository,
        private val userRepository: UserRepository,
        private val jdbcTemplate: JdbcTemplate,
    ) {
        private val now = Instant.parse("2026-01-01T00:00:00Z")

        @Test
        fun `V11 로그인 state 테이블에 code verifier 컬럼이 있다`() {
            val columnCount =
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'auth_login_states' AND column_name = 'code_verifier'",
                    Int::class.java,
                )

            assertEquals(1, columnCount)
        }

        @Test
        fun `state는 한 번만 소모된다`() {
            repository.saveState("hash-1", "/teams", "pkce-verifier", now, now.plusSeconds(300))

            val state = repository.consumeState("hash-1", now)
            assertEquals("/teams", state?.redirect)
            assertEquals("pkce-verifier", state?.codeVerifier)
            assertNull(repository.consumeState("hash-1", now))
        }

        @Test
        fun `만료된 state는 소모되지 않는다`() {
            repository.saveState("hash-2", "/", now, now.plusSeconds(300))

            assertNull(repository.consumeState("hash-2", now.plusSeconds(300)))
        }

        @Test
        fun `임시 코드는 사용자와 redirect를 돌려주고 한 번만 소모된다`() {
            userRepository.saveAndFlush(user("user-0123456789ab"))
            repository.saveCode("code-1", "user-0123456789ab", "/a", now, now.plusSeconds(60))

            val code = repository.consumeCode("code-1", now)

            assertEquals("user-0123456789ab", code?.userId)
            assertEquals("/a", code?.redirect)
            assertNull(repository.consumeCode("code-1", now))
        }

        @Test
        fun `만료된 임시 코드는 소모되지 않는다`() {
            userRepository.saveAndFlush(user("user-0123456789ab"))
            repository.saveCode("code-2", "user-0123456789ab", "/", now, now.plusSeconds(60))

            assertNull(repository.consumeCode("code-2", now.plusSeconds(61)))
        }

        @Test
        fun `만료된 행만 정리한다`() {
            userRepository.saveAndFlush(user("user-0123456789ab"))
            repository.saveState("old-state", "/", now, now.plusSeconds(10))
            repository.saveState("new-state", "/", now, now.plusSeconds(300))
            repository.saveCode("old-code", "user-0123456789ab", "/", now, now.plusSeconds(10))

            val (states, codes) = repository.deleteExpired(now.plusSeconds(60))

            assertEquals(1, states)
            assertEquals(1, codes)
            assertNotNull(repository.consumeState("new-state", now.plusSeconds(60)))
        }

        private fun user(id: String) =
            User(
                id = id,
                dataGsmId = "school-1",
                email = "student@example.com",
                name = "학생",
                role = Role.USER,
                status = UserStatus.ACTIVE,
                grade = null,
                classNumber = null,
                studentNumber = null,
                createdAt = now,
            )
    }
