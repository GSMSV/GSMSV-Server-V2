package com.gsmsv.api.app.domain

import com.gsmsv.api.global.authorization.OwnedResource
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.SubResourceType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "apps")
class App(
    @Id
    @Column(length = 16, nullable = false)
    override val id: String,
    initialName: String,
    initialOwner: Owner,
    @Column(name = "created_by", nullable = false, length = 17, updatable = false)
    val createdByUserId: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    initialCreatedAt: Instant = Instant.now(),
) : OwnedResource {
    @Column(nullable = false, length = 30)
    final var name: String = normalizeName(initialName)
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 4, updatable = false)
    private val ownerType: AppOwnerType =
        when (initialOwner) {
            is Owner.User -> AppOwnerType.USER
            is Owner.Team -> AppOwnerType.TEAM
        }

    @Column(name = "owner_id", nullable = false, length = 17, updatable = false)
    private val ownerId: String = initialOwner.id

    @Column(nullable = false, columnDefinition = "text")
    val status: String = "creating"

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = initialCreatedAt

    @Column(name = "updated_at", nullable = false)
    final var updatedAt: Instant = initialCreatedAt
        private set

    @OneToMany(mappedBy = "app", cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    private val mutableEnvironments: MutableList<AppEnvironment> = mutableListOf()

    val environments: List<AppEnvironment>
        get() = mutableEnvironments.toList()

    override val owner: Owner
        get() =
            when (ownerType) {
                AppOwnerType.USER -> Owner.User(ownerId)
                AppOwnerType.TEAM -> Owner.Team(ownerId)
            }

    init {
        mutableEnvironments.add(AppEnvironment(IdGenerator.subResourceId(SubResourceType.ENVIRONMENT), this, "production"))
    }

    fun rename(name: String) {
        this.name = normalizeName(name)
    }

    @PreUpdate
    fun updateTimestamp() {
        updatedAt = Instant.now()
    }

    companion object {
        private fun normalizeName(name: String): String {
            val normalized = name.trim()
            require(normalized.codePointCount(0, normalized.length) in 1..30) {
                "앱 이름은 앞뒤 공백을 제거한 뒤 1~30자여야 합니다."
            }
            return normalized
        }
    }
}
