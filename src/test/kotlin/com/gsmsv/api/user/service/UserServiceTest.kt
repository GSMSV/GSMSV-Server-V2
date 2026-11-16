package com.gsmsv.api.user.service

import com.gsmsv.api.global.security.Role
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.domain.UserStatus
import com.gsmsv.api.user.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UserServiceTest {
    private val userRepository = mockk<UserRepository>()
    private val userService = UserService(userRepository)

    @Test
    fun `DataGSM 식별자가 없으면 새 활성 사용자로 저장한다`() {
        every { userRepository.findByDataGsmIdAndDeletedAtIsNull("school-123") } returns null
        every { userRepository.save(any()) } answers { firstArg() }

        val user = userService.findOrCreateFromDataGsm("school-123", "student@example.com", "홍길동", 2, 3, 12)

        assertEquals(Role.USER, user.role)
        assertEquals(UserStatus.ACTIVE, user.status)
        assertEquals("student@example.com", user.email)
        assertEquals(2, user.grade)
        assertEquals(3, user.classNumber)
        assertEquals(12, user.studentNumber)
        verify(exactly = 1) { userRepository.save(any()) }
    }

    @Test
    fun `기존 DataGSM 사용자는 같은 계정의 학교 정보를 갱신한다`() {
        val existingUser =
            User(
                id = "user-0123456789ab",
                dataGsmId = "school-123",
                email = "old@example.com",
                name = "이전 이름",
                role = Role.ADMIN,
                status = UserStatus.INACTIVE,
            )
        every { userRepository.findByDataGsmIdAndDeletedAtIsNull("school-123") } returns existingUser

        val updatedUser = userService.findOrCreateFromDataGsm("school-123", "new@example.com", "홍길동", 3, 1, 2)

        assertEquals(existingUser.id, updatedUser.id)
        assertEquals("new@example.com", updatedUser.email)
        assertEquals("홍길동", updatedUser.name)
        assertEquals(3, updatedUser.grade)
        assertEquals(1, updatedUser.classNumber)
        assertEquals(2, updatedUser.studentNumber)
        assertEquals(Role.ADMIN, updatedUser.role)
        assertEquals(UserStatus.INACTIVE, updatedUser.status)
        verify(exactly = 0) { userRepository.save(any()) }
    }
}
