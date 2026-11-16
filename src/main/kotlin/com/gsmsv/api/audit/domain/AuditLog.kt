package com.gsmsv.api.audit.domain

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 감사 대상 동작 1건. 한 번 쓰면 바꾸지 않는다.
 *
 * 자원과 사용자가 지워져도 남아야 해서 FK 없이 값으로 저장한다. 비밀값은 어느 칸에도 넣지 않는다.
 */
@Entity
@Table(name = "audit_logs")
class AuditLog(
    @Column(name = "request_id", length = 70, updatable = false)
    val requestId: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 16)
    val actorType: AuditActorType,
    @Column(name = "actor_id", length = 17, updatable = false)
    val actorId: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 48)
    val eventType: AuditEventType,
    @Column(nullable = false, updatable = false)
    val target: String,
    @Convert(converter = AuditDetailsConverter::class)
    @Column(updatable = false)
    val details: Map<String, String> = emptyMap(),
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(insertable = false, updatable = false)
    val id: Long? = null

    init {
        require((actorType == AuditActorType.SYSTEM) == (actorId == null)) { "SYSTEM만 주체 id가 없습니다." }
    }
}
