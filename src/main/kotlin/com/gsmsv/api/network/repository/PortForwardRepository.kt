package com.gsmsv.api.network.repository

import com.gsmsv.api.network.domain.PortForward
import com.gsmsv.api.network.domain.PortForwardKind
import org.springframework.data.jpa.repository.JpaRepository

/** VM id로만 꺼낸다. VM id는 로더를 통과한 VM에서 받는다 */
interface PortForwardRepository : JpaRepository<PortForward, String> {
    fun findAllByVmIdOrderByCreatedAtAscIdAsc(vmId: String): List<PortForward>

    fun countByVmIdAndKind(
        vmId: String,
        kind: PortForwardKind,
    ): Long

    fun existsByExternalPort(externalPort: Int): Boolean
}
