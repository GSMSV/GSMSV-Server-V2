package com.gsmsv.api.network.repository

import com.gsmsv.api.network.domain.PortForward
import com.gsmsv.api.network.domain.PortForwardKind
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/** VM id로만 꺼낸다. VM id는 로더를 통과한 VM에서 받는다 */
interface PortForwardRepository : JpaRepository<PortForward, String> {
    fun findAllByVmIdOrderByCreatedAtAscIdAsc(vmId: String): List<PortForward>

    fun countByVmIdAndKind(
        vmId: String,
        kind: PortForwardKind,
    ): Long

    fun existsByExternalPort(externalPort: Int): Boolean

    /** 후보 중 이미 쓰는 외부 포트만 한 번에 꺼낸다 */
    @Query("select p.externalPort from PortForward p where p.externalPort in :ports")
    fun findUsedExternalPorts(ports: Collection<Int>): List<Int>
}
