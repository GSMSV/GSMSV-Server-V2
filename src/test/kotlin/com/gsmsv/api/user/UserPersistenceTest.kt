package com.gsmsv.api.user

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
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="])
@Tag("integration")
@Transactional
class UserPersistenceTest
    @Autowired
    constructor(
        private val userRepository: UserRepository,
    ) {
        @Test
        fun `활성 계정은 같은 이메일을 사용할 수 없다`() {
            userRepository.saveAndFlush(user("user-0123456789ab", "school-123", "student@example.com"))

            assertFailsWith<DataIntegrityViolationException> {
                userRepository.saveAndFlush(user("user-1123456789ab", "school-456", "student@example.com"))
            }
        }

        @Test
        fun `탈퇴 계정의 이메일과 DataGSM ID는 새 계정에서 다시 사용할 수 있다`() {
            userRepository.saveAndFlush(user("user-0123456789ab", "school-123", "student@example.com"))
            val withdrawnUser = userRepository.findById("user-0123456789ab").orElseThrow()
            withdrawnUser.withdraw()
            userRepository.saveAndFlush(withdrawnUser)

            val replacement = user("user-2123456789ab", "school-123", "student@example.com")
            userRepository.saveAndFlush(replacement)

            assertEquals("user-2123456789ab", userRepository.findByDataGsmIdAndDeletedAtIsNull("school-123")?.id)
        }

        private fun user(
            id: String,
            dataGsmId: String,
            email: String,
        ) = User(
            id = id,
            dataGsmId = dataGsmId,
            email = email,
            name = "홍길동",
            role = Role.USER,
            status = UserStatus.ACTIVE,
        )
    }
