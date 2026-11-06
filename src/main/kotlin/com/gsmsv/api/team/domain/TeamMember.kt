package com.gsmsv.api.team.domain

import com.gsmsv.api.global.authorization.TeamRole
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "team_members")
class TeamMember(
    @Id
    @Column(length = 16, nullable = false)
    val id: String,
    @Column(name = "team_id", length = 17, nullable = false)
    val teamId: String,
    @Column(name = "user_id", length = 17, nullable = false)
    val userId: String,
    initialRole: TeamRole,
) {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    final var role: TeamRole = initialRole
        private set

    fun changeRole(role: TeamRole) {
        this.role = role
    }
}
