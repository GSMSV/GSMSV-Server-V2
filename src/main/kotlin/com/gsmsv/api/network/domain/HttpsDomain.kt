package com.gsmsv.api.network.domain

import com.gsmsv.api.global.authorization.SubResource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

/**
 * VM 1대에 연결한 HTTPS 도메인 (Network 5.10~5.13). 부모 VM 아래의 하위 자원(`domain-…`)이다.
 *
 * DB가 진실이다 (23번 9장). 게이트웨이 라우트는 이 표의 행 전체로 만들고, 라우트 식별값은 [id]다.
 * 서브도메인은 전체 서비스에서 하나만 쓸 수 있다 (유니크 제약).
 */
@Entity
@Table(name = "https_domains")
class HttpsDomain(
    @Id
    @Column(length = 15, nullable = false)
    override val id: String,
    @Column(name = "vm_id", nullable = false, updatable = false, length = 15)
    val vmId: String,
    initialSubdomain: String,
    initialInternalPort: Int,
    initialCreatedAt: Instant = Instant.now(),
) : SubResource {
    @Column(nullable = false, length = 63)
    final var subdomain: String = initialSubdomain
        private set

    @Column(name = "internal_port", nullable = false)
    final var internalPort: Int = initialInternalPort
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 8)
    final var syncStatus: SyncStatus = SyncStatus.PENDING
        private set

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = initialCreatedAt

    @Column(name = "updated_at", nullable = false)
    final var updatedAt: Instant = initialCreatedAt
        private set

    override val parentId: String
        get() = vmId

    /** 게이트웨이가 이 도메인이 든 라우트 목록 전체를 반영했을 때 */
    fun markSynced() {
        syncStatus = SyncStatus.SYNCED
    }

    @PreUpdate
    fun updateTimestamp() {
        updatedAt = Instant.now()
    }
}

/** 게이트웨이 반영 상태. 응답에는 소문자로 나간다 (`pending`, `synced`, `failed`) */
enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED,
    ;

    val wire: String
        get() = name.lowercase()
}
