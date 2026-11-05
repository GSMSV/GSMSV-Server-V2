package com.gsmsv.api.user.domain

import com.gsmsv.api.global.security.Role
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users")
class User(
    @Id
    @Column(length = 17, nullable = false)
    val id: String,
    dataGsmId: String,
    email: String,
    name: String,
    role: Role = Role.USER,
    status: UserStatus = UserStatus.ACTIVE,
    grade: Int? = null,
    classNumber: Int? = null,
    studentNumber: Int? = null,
    createdAt: Instant = Instant.now(),
    deletedAt: Instant? = null,
) {
    @Column(name = "datagsm_id", nullable = false)
    val dataGsmId: String = dataGsmId

    @Column(nullable = false)
    final var email: String = email
        private set

    @Column(nullable = false)
    final var name: String = name
        private set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    final var role: Role = role
        private set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    final var status: UserStatus = status
        private set

    final var grade: Int? = grade
        private set

    @Column(name = "class_number")
    final var classNumber: Int? = classNumber
        private set

    @Column(name = "student_number")
    final var studentNumber: Int? = studentNumber
        private set

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = createdAt

    @Column(name = "deleted_at")
    final var deletedAt: Instant? = deletedAt
        private set

    fun updateFromDataGsm(
        email: String,
        name: String,
        grade: Int?,
        classNumber: Int?,
        studentNumber: Int?,
    ) {
        this.email = email
        this.name = name
        this.grade = grade
        this.classNumber = classNumber
        this.studentNumber = studentNumber
    }

    fun deactivate() {
        status = UserStatus.INACTIVE
    }

    fun withdraw(at: Instant = Instant.now()) {
        status = UserStatus.INACTIVE
        deletedAt = at
    }
}
