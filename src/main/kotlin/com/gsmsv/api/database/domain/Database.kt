package com.gsmsv.api.database.domain

import com.gsmsv.api.global.authorization.OwnedResource
import com.gsmsv.api.global.authorization.Owner
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

/**
 * 공유 DB 서버 안의 database 1개와 그 계정 1개를 센 DB 자원 (24번 DBaaS).
 *
 * 서버 쪽 database·계정 이름은 [id]를 그대로 쓰고 사용자가 지은 [name]은 쓰지 않는다.
 * 소유자는 만든 뒤 바꾸지 않는다. 삭제해도 행은 지우지 않고 [deletedAt]만 남긴다.
 */
@Entity
@Table(name = "databases")
class Database(
    @Id
    @Column(length = 15, nullable = false)
    override val id: String,
    initialName: String,
    @Column(name = "owner_type", nullable = false, updatable = false, length = 8)
    @Enumerated(EnumType.STRING)
    private val ownerType: OwnerType,
    @Column(name = "owner_id", nullable = false, updatable = false, length = 17)
    private val ownerId: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    val engine: DatabaseEngine,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    val isolation: DatabaseIsolation = DatabaseIsolation.SHARED,
    @Column(name = "max_connections", nullable = false, updatable = false)
    val maxConnections: Int = DEFAULT_MAX_CONNECTIONS,
    @Column(name = "created_by", nullable = false, updatable = false, length = 17)
    val createdBy: String,
    initialExpiresAt: Instant,
    initialCreatedAt: Instant = Instant.now(),
) : OwnedResource {
    @Column(nullable = false)
    final var name: String = initialName
        private set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    final var status: DatabaseStatus = DatabaseStatus.CREATING
        private set

    @Column(name = "write_locked", nullable = false)
    final var writeLocked: Boolean = false
        private set

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = initialCreatedAt

    @Column(name = "updated_at", nullable = false)
    final var updatedAt: Instant = initialCreatedAt
        private set

    @Column(name = "expires_at", nullable = false)
    final var expiresAt: Instant = initialExpiresAt
        private set

    @Column(name = "deleted_at")
    final var deletedAt: Instant? = null
        private set

    /** 계정 비밀번호를 암호화한 값. 평문은 두지 않는다. 공유 DB 서버에 계정을 만들기 전에는 없다 */
    @Column(name = "password_encrypted", columnDefinition = "text")
    final var passwordEncrypted: String? = null
        private set

    override val owner: Owner
        get() =
            when (ownerType) {
                OwnerType.USER -> Owner.User(ownerId)
                OwnerType.TEAM -> Owner.Team(ownerId)
            }

    fun rename(name: String) {
        this.name = name
    }

    /** 공유 DB 서버에 database와 계정을 다 만들었을 때 */
    fun markActive() {
        check(status == DatabaseStatus.CREATING) { "만드는 중인 DB만 사용 중으로 바꿀 수 있습니다." }
        status = DatabaseStatus.ACTIVE
    }

    fun markFailed() {
        check(status == DatabaseStatus.CREATING) { "만드는 중인 DB만 실패로 바꿀 수 있습니다." }
        status = DatabaseStatus.FAILED
    }

    /** 만료 정지. 계정을 잠그고 데이터는 남긴다 */
    fun suspend() {
        check(status == DatabaseStatus.ACTIVE) { "사용 중인 DB만 정지할 수 있습니다." }
        status = DatabaseStatus.SUSPENDED
    }

    /** 사용 기간을 늘린다. 정지 중이면 사용 중으로 돌아간다 */
    fun extend(newExpiresAt: Instant) {
        check(status == DatabaseStatus.ACTIVE || status == DatabaseStatus.SUSPENDED) {
            "사용 중이거나 정지된 DB만 연장할 수 있습니다."
        }
        require(newExpiresAt.isAfter(expiresAt)) { "만료 시각은 앞으로만 늘릴 수 있습니다." }
        expiresAt = newExpiresAt
        if (status == DatabaseStatus.SUSPENDED) {
            status = DatabaseStatus.ACTIVE
        }
    }

    /** 계정을 만들거나 비밀번호를 바꾼 뒤 암호화한 새 값을 남긴다 */
    fun changePassword(encrypted: String) {
        require(encrypted.isNotBlank()) { "암호화한 비밀번호가 비어 있습니다." }
        passwordEncrypted = encrypted
    }

    fun lockWrites() {
        writeLocked = true
    }

    fun unlockWrites() {
        writeLocked = false
    }

    fun startDeleting() {
        check(status in DELETABLE) { "이 상태의 DB는 삭제를 시작할 수 없습니다." }
        status = DatabaseStatus.DELETING
    }

    /** 서버의 database와 계정을 지운 뒤 삭제 표시를 남긴다. 행은 지우지 않는다 */
    fun markDeleted(at: Instant = Instant.now()) {
        check(status == DatabaseStatus.DELETING) { "삭제 중인 DB만 삭제됨으로 바꿀 수 있습니다." }
        status = DatabaseStatus.DELETED
        deletedAt = at
    }

    @PreUpdate
    fun updateTimestamp() {
        updatedAt = Instant.now()
    }

    enum class OwnerType {
        USER,
        TEAM,
    }

    companion object {
        /** 계정당 동시 연결 수 (24번 DBaaS 7장). 설정값으로 옮길 때까지 기본값으로 둔다 */
        const val DEFAULT_MAX_CONNECTIONS = 10

        private val DELETABLE = setOf(DatabaseStatus.ACTIVE, DatabaseStatus.SUSPENDED, DatabaseStatus.FAILED)

        fun create(
            id: String,
            name: String,
            owner: Owner,
            engine: DatabaseEngine,
            createdBy: String,
            expiresAt: Instant,
        ): Database =
            Database(
                id = id,
                initialName = name,
                ownerType =
                    when (owner) {
                        is Owner.User -> OwnerType.USER
                        is Owner.Team -> OwnerType.TEAM
                    },
                ownerId = owner.id,
                engine = engine,
                createdBy = createdBy,
                initialExpiresAt = expiresAt,
            )
    }
}
