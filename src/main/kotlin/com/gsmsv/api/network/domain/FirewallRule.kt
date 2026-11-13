package com.gsmsv.api.network.domain

import com.gsmsv.api.global.authorization.SubResource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * VM 하나의 방화벽 규칙 1개 (5.14~5.16). VM 아래의 하위 자원이다 (`fwrule-…`).
 *
 * 만든 뒤 바꾸지 않는다. 목적지는 저장하지 않는다. Go에 보낼 때 그 VM의 내부 IP로 강제한다.
 * Go가 가상화 서버 규칙의 주석에 [id]를 적어, 순번이 아니라 id로 규칙을 가리킨다.
 */
@Entity
@Table(name = "firewall_rules")
class FirewallRule(
    @Id
    @Column(length = 15, nullable = false)
    override val id: String,
    @Column(name = "vm_id", nullable = false, updatable = false, length = 15)
    val vmId: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 8)
    val action: FirewallAction,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 8)
    val direction: FirewallDirection,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 8)
    val protocol: FirewallProtocol,
    @Column(updatable = false, length = 11)
    val dport: String?,
    @Column(updatable = false, length = 49)
    val source: String?,
    // 커서에 담는 값이 DB에 저장된 값과 같도록 DB 정밀도(마이크로초)로 자른다
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS),
) : SubResource {
    override val parentId: String
        get() = vmId

    companion object {
        fun create(
            id: String,
            vmId: String,
            spec: FirewallRuleSpec,
        ): FirewallRule =
            FirewallRule(
                id = id,
                vmId = vmId,
                action = spec.action,
                direction = spec.direction,
                protocol = spec.protocol,
                dport = spec.dport,
                source = spec.source,
            )
    }
}
