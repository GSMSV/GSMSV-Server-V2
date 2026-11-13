package com.gsmsv.api.team.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "team_invitations")
class TeamInvitation(
    @Id
    @Column(length = 16, nullable = false)
    val id: String,
    @Column(name = "team_id", length = 17, nullable = false)
    val teamId: String,
    @Column(nullable = false)
    val email: String,
    initialTokenHash: String,
    initialSentAt: Instant,
    initialExpiresAt: Instant,
    initialCanceledAt: Instant? = null,
    initialAcceptedAt: Instant? = null,
) {
    @Column(name = "sent_at", nullable = false)
    final var sentAt: Instant = initialSentAt
        private set

    @Column(name = "token_hash", nullable = false, length = 64)
    final var tokenHash: String = initialTokenHash
        private set

    @Column(name = "expires_at", nullable = false)
    final var expiresAt: Instant = initialExpiresAt
        private set

    @Column(name = "canceled_at")
    final var canceledAt: Instant? = initialCanceledAt
        private set

    @Column(name = "accepted_at")
    final var acceptedAt: Instant? = initialAcceptedAt
        private set

    fun reissue(
        tokenHash: String,
        sentAt: Instant,
        expiresAt: Instant,
    ) {
        this.tokenHash = tokenHash
        this.sentAt = sentAt
        this.expiresAt = expiresAt
    }

    fun cancel(at: Instant) {
        canceledAt = at
    }

    fun accept(at: Instant) {
        acceptedAt = at
    }
}
