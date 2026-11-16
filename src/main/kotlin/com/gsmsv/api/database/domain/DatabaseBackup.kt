package com.gsmsv.api.database.domain

import com.gsmsv.api.global.authorization.SubResource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * DB 1개의 백업 기록 (24번 DBaaS). 백업 파일은 [storageKey]로 `BackupStorage`에서 찾는다.
 *
 * 만든 뒤 바꾸지 않는다. 만료되면 파일과 함께 행도 지운다.
 */
@Entity
@Table(name = "database_backups")
class DatabaseBackup(
    @Id
    @Column(length = 15, nullable = false)
    override val id: String,
    @Column(name = "database_id", nullable = false, updatable = false, length = 15)
    val databaseId: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    val kind: DatabaseBackupKind,
    @Column(name = "size_bytes", nullable = false, updatable = false)
    val sizeBytes: Long,
    @Column(name = "storage_key", nullable = false, updatable = false)
    val storageKey: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant,
) : SubResource {
    override val parentId: String
        get() = databaseId

    /** 보관 기간이 지났는지. 목록(7.10)과 같은 기준이다 */
    fun isExpiredAt(now: Instant): Boolean = !expiresAt.isAfter(now)
}
