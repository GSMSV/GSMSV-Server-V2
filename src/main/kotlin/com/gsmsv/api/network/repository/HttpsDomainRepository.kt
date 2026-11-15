package com.gsmsv.api.network.repository

import com.gsmsv.api.network.domain.HttpsDomain
import org.springframework.data.jpa.repository.JpaRepository

/** 부모 VM은 로더가 통과시킨 뒤에만 넘어온다. VM id 없이 하위 id만으로 찾는 메서드는 두지 않는다 (22번 5.3절) */
interface HttpsDomainRepository : JpaRepository<HttpsDomain, String> {
    fun findAllByVmIdOrderByCreatedAtAscIdAsc(vmId: String): List<HttpsDomain>

    fun countByVmId(vmId: String): Long

    fun existsBySubdomain(subdomain: String): Boolean

    /** 라우트 목록 전체를 만들 때만 쓴다. 순서를 고정해 같은 상태면 같은 목록을 보낸다 */
    fun findAllByOrderByCreatedAtAscIdAsc(): List<HttpsDomain>
}
