package com.gsmsv.api.network.repository

import com.gsmsv.api.network.domain.FirewallRule
import com.gsmsv.api.network.domain.VmFirewall
import org.springframework.data.jpa.repository.JpaRepository

/** id 조회는 [com.gsmsv.api.network.service.FirewallRuleResourceReader] 밖에서 부르지 않는다 (22번 규칙). */
interface FirewallRuleRepository : JpaRepository<FirewallRule, String> {
    /** 로더를 통과한 VM의 id로만 부른다. 만든 순서이고, Go에 보내는 순서도 이와 같다 */
    fun findAllByVmIdOrderByCreatedAtAscIdAsc(vmId: String): List<FirewallRule>

    fun countByVmId(vmId: String): Long
}

interface VmFirewallRepository : JpaRepository<VmFirewall, String>
