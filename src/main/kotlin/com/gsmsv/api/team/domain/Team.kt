package com.gsmsv.api.team.domain

import com.gsmsv.api.global.authorization.TeamResource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "teams")
class Team(
    @Id
    @Column(length = 17, nullable = false)
    override val id: String,
    name: String,
    status: TeamStatus = TeamStatus.PENDING,
    rejectionReason: String? = null,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = createdAt,
) : TeamResource {
    @Column(nullable = false)
    final var name: String = name
        private set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    final var status: TeamStatus = status
        private set

    @Column(name = "rejection_reason")
    final var rejectionReason: String? = rejectionReason
        private set

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = createdAt

    @Column(name = "updated_at", nullable = false)
    final var updatedAt: Instant = updatedAt
        private set

    override val approved: Boolean
        get() = status == TeamStatus.APPROVED

    fun rename(name: String) {
        this.name = name
    }

    fun approve() {
        check(status == TeamStatus.PENDING) { "승인 대기 중인 팀만 승인할 수 있습니다." }
        status = TeamStatus.APPROVED
        rejectionReason = null
    }

    fun reject(reason: String) {
        check(status == TeamStatus.PENDING) { "승인 대기 중인 팀만 거절할 수 있습니다." }
        require(reason.isNotBlank()) { "거절 사유는 비어 있을 수 없습니다." }
        status = TeamStatus.REJECTED
        rejectionReason = reason
    }

    @PreUpdate
    fun updateTimestamp() {
        updatedAt = Instant.now()
    }
}
