package com.gsmsv.api.global.idempotency

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 멱등성 키 1건. 만들기와 응답 저장은 [IdempotencyRecordRepository]의 쿼리로만 하고, 엔티티는 읽기용이다 */
@Entity
@Table(name = "idempotency_keys")
class IdempotencyRecord(
    @Id
    @Column(length = 36, nullable = false)
    val id: String,
    @Column(name = "user_id", nullable = false, updatable = false, length = 17)
    val userId: String,
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    val idempotencyKey: String,
    @Column(nullable = false, updatable = false, length = 64)
    val fingerprint: String,
    @Column(name = "response_status")
    val responseStatus: Int?,
    @Column(name = "content_type")
    val contentType: String?,
    @Column(name = "response_body")
    val responseBody: ByteArray?,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant,
)
