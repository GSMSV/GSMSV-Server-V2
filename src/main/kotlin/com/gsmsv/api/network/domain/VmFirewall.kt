package com.gsmsv.api.network.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * VM 방화벽을 켰는지 (5.14 `enabled`). 규칙을 처음 추가할 때 만들고 켠다.
 *
 * 규칙을 모두 지워도 켜진 채로 둔다 (5.16). 끄는 API는 계약에 없다.
 */
@Entity
@Table(name = "vm_firewalls")
class VmFirewall(
    @Id
    @Column(name = "vm_id", length = 15, nullable = false)
    val vmId: String,
) {
    @Column(nullable = false)
    final var enabled: Boolean = false
        private set

    fun enable() {
        enabled = true
    }
}
