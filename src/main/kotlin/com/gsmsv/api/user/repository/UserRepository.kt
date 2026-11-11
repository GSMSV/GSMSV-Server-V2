package com.gsmsv.api.user.repository

import com.gsmsv.api.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, String> {
    fun findByDataGsmIdAndDeletedAtIsNull(dataGsmId: String): User?

    fun findByEmailAndDeletedAtIsNull(email: String): User?
}
