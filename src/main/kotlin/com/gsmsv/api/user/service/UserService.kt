package com.gsmsv.api.user.service

import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.domain.UserStatus
import com.gsmsv.api.user.dto.UserSummary
import com.gsmsv.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun findById(id: String): User? = userRepository.findById(id).orElse(null)

    @Transactional(readOnly = true)
    fun findByEmail(email: String): User? = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)

    @Transactional(readOnly = true)
    fun findSummariesByIds(userIds: Collection<String>): Map<String, UserSummary> =
        userRepository
            .findAllById(userIds)
            .associate { user -> user.id to UserSummary(user.id, user.name, user.email) }

    @Transactional
    fun findOrCreateFromDataGsm(
        dataGsmId: String,
        email: String,
        name: String,
        grade: Int?,
        classNumber: Int?,
        studentNumber: Int?,
    ): User {
        val existingUser = userRepository.findByDataGsmIdAndDeletedAtIsNull(dataGsmId)
        if (existingUser != null) {
            existingUser.updateFromDataGsm(email, name, grade, classNumber, studentNumber)
            return existingUser
        }

        return userRepository.save(
            User(
                id = IdGenerator.userId(),
                dataGsmId = dataGsmId,
                email = email,
                name = name,
                role = Role.USER,
                status = UserStatus.ACTIVE,
                grade = grade,
                classNumber = classNumber,
                studentNumber = studentNumber,
            ),
        )
    }
}
