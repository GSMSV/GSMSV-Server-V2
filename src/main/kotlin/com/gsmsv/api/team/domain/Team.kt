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
    initialName: String,
    initialStatus: TeamStatus = TeamStatus.PENDING,
    initialRejectionReason: String? = null,
    initialCreatedAt: Instant = Instant.now(),
    initialUpdatedAt: Instant = initialCreatedAt,
) : TeamResource {
    @Column(nullable = false)
    final var name: String = initialName
        private set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    final var status: TeamStatus = initialStatus
        private set

    @Column(name = "rejection_reason")
    final var rejectionReason: String? = initialRejectionReason
        private set

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = initialCreatedAt

    @Column(name = "updated_at", nullable = false)
    final var updatedAt: Instant = initialUpdatedAt
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
