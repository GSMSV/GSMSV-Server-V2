package com.gsmsv.api.network.service

import com.gsmsv.api.global.authorization.SubResourceReader
import com.gsmsv.api.global.grn.SubResourceType
import com.gsmsv.api.network.domain.FirewallRule
import com.gsmsv.api.network.repository.FirewallRuleRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 방화벽 규칙 하위 자원 조회 포트. 부모 VM이 요청과 같은지는 `SubResourceLoader`가 확인한다. */
@Component
class FirewallRuleResourceReader(
    private val ruleRepository: FirewallRuleRepository,
) : SubResourceReader<FirewallRule> {
    override val type: SubResourceType = SubResourceType.FIREWALL_RULE

    @Transactional(readOnly = true)
    override fun findById(id: String): FirewallRule? = ruleRepository.findById(id).orElse(null)
}
