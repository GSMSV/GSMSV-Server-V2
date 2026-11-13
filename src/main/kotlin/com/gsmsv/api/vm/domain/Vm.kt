package com.gsmsv.api.vm.domain

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
 * 사용자가 만든 가상 머신 1대 (인스턴스 6번). GRN이 있는 서비스 자원이다.
 *
 * 소유자와 사양은 만든 뒤 바꾸지 않는다. 삭제해도 행은 지우지 않고 [deletedAt]만 남긴다.
 * 전원·CPU·메모리 사용률·가동 시간은 저장하지 않는다.
 * 초기 비밀번호는 암호화한 값만 가진다. 평문은 이 객체 밖에서 암호화한 뒤 넘긴다.
 */
@Entity
@Table(name = "vms")
class Vm(
    @Id
    @Column(length = 15, nullable = false)
    override val id: String,
    initialName: String,
    @Column(columnDefinition = "text")
    val purpose: String?,
    @Column(name = "owner_type", nullable = false, updatable = false, length = 8)
    @Enumerated(EnumType.STRING)
    private val ownerType: OwnerType,
    @Column(name = "owner_id", nullable = false, updatable = false, length = 17)
    private val ownerId: String,
    @Column(nullable = false, updatable = false, length = 64)
    val image: String,
    @Column(nullable = false, updatable = false)
    val vcpu: Int,
    @Column(name = "ram_gb", nullable = false, updatable = false)
    val ramGb: Int,
    @Column(name = "ssd_gb", nullable = false, updatable = false)
    val ssdGb: Int,
    @Column(name = "initial_username", nullable = false, updatable = false, length = 32)
    val initialUsername: String,
    @Column(name = "initial_password_encrypted", nullable = false, updatable = false, columnDefinition = "text")
    val initialPasswordEncrypted: String,
    @Column(name = "created_by", nullable = false, updatable = false, length = 17)
    val createdBy: String,
    initialExpiresAt: Instant,
    initialCreatedAt: Instant = Instant.now(),
) : OwnedResource {
    @Column(nullable = false, columnDefinition = "text")
    final var name: String = initialName
        private set

    @Column(name = "internal_ip", length = 45)
    final var internalIp: String? = null
        private set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    final var status: VmStatus = VmStatus.CREATING
        private set

    /** Go가 보고한 cloud-init 완료 여부를 저장한 값이다. 목록·상세가 같은 값을 쓴다 */
    @Column(nullable = false)
    final var initialized: Boolean = false
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

    override val owner: Owner
        get() =
            when (ownerType) {
                OwnerType.USER -> Owner.User(ownerId)
                OwnerType.TEAM -> Owner.Team(ownerId)
            }

    fun rename(name: String) {
        this.name = name
    }

    /** VM을 만들고 내부 IP를 받았을 때 */
    fun markActive(internalIp: String) {
        check(status == VmStatus.CREATING) { "만드는 중인 VM만 사용 중으로 바꿀 수 있습니다." }
        this.internalIp = internalIp
        status = VmStatus.ACTIVE
    }

    fun markFailed() {
        check(status == VmStatus.CREATING) { "만드는 중인 VM만 실패로 바꿀 수 있습니다." }
        status = VmStatus.FAILED
    }

    fun markInitialized() {
        initialized = true
    }

    /** 만료 정지 */
    fun suspend() {
        check(status == VmStatus.ACTIVE) { "사용 중인 VM만 정지할 수 있습니다." }
        status = VmStatus.SUSPENDED
    }

    /** 사용 기간을 늘린다. 정지 중이면 사용 중으로 돌아간다 */
    fun extend(newExpiresAt: Instant) {
        check(status == VmStatus.ACTIVE || status == VmStatus.SUSPENDED) {
            "사용 중이거나 정지된 VM만 연장할 수 있습니다."
        }
        require(newExpiresAt.isAfter(expiresAt)) { "만료 시각은 앞으로만 늘릴 수 있습니다." }
        expiresAt = newExpiresAt
        if (status == VmStatus.SUSPENDED) {
            status = VmStatus.ACTIVE
        }
    }

    fun startDeleting() {
        check(status in DELETABLE) { "이 상태의 VM은 삭제를 시작할 수 없습니다." }
        status = VmStatus.DELETING
    }

    /** 가상화 서버의 VM을 지운 뒤 삭제 표시를 남긴다. 행은 지우지 않는다 */
    fun markDeleted(at: Instant = Instant.now()) {
        check(status == VmStatus.DELETING) { "삭제 중인 VM만 삭제됨으로 바꿀 수 있습니다." }
        status = VmStatus.DELETED
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
        private val DELETABLE = setOf(VmStatus.ACTIVE, VmStatus.SUSPENDED, VmStatus.FAILED)

        fun create(
            id: String,
            name: String,
            purpose: String?,
            owner: Owner,
            image: String,
            vcpu: Int,
            ramGb: Int,
            ssdGb: Int,
            initialUsername: String,
            initialPasswordEncrypted: String,
            createdBy: String,
            expiresAt: Instant,
        ): Vm =
            Vm(
                id = id,
                initialName = name,
                purpose = purpose,
                ownerType =
                    when (owner) {
                        is Owner.User -> OwnerType.USER
                        is Owner.Team -> OwnerType.TEAM
                    },
                ownerId = owner.id,
                image = image,
                vcpu = vcpu,
                ramGb = ramGb,
                ssdGb = ssdGb,
                initialUsername = initialUsername,
                initialPasswordEncrypted = initialPasswordEncrypted,
                createdBy = createdBy,
                initialExpiresAt = expiresAt,
            )
    }
}
