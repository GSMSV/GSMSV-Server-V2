package com.gsmsv.api.network.service

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.SubResourceType
import com.gsmsv.api.network.domain.FirewallRule
import com.gsmsv.api.network.domain.FirewallRuleSpec
import com.gsmsv.api.network.domain.VmFirewall
import com.gsmsv.api.network.repository.FirewallRuleRepository
import com.gsmsv.api.network.repository.VmFirewallRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** VM 하나의 방화벽 원하는 상태. Go에 그대로 보낸다 */
data class FirewallState(
    val enabled: Boolean,
    val rules: List<FirewallRule>,
)

/** 규칙을 저장한 결과. [firewallCreated]면 이번에 방화벽 행을 처음 만들었다 (되돌릴 때 함께 지운다) */
data class AddedFirewallRule(
    val rule: FirewallRule,
    val firewallCreated: Boolean,
    val state: FirewallState,
)

/**
 * 방화벽 규칙의 DB 쪽 작업. 트랜잭션마다 짧게 끝내고, Go 호출은 [FirewallRuleService]가 트랜잭션 밖에서 한다
 * (내부 API 공통 규약 7장: DB 트랜잭션 안에서 Infra를 부르지 않는다).
 */
@Service
class FirewallRuleStore(
    private val ruleRepository: FirewallRuleRepository,
    private val firewallRepository: VmFirewallRepository,
) {
    /**
     * 규칙을 저장하고 방화벽을 켠다.
     *
     * @throws BusinessException VM당 한도를 넘으면 [ErrorCode.QUOTA_EXCEEDED]
     */
    @Transactional
    fun add(
        vmId: String,
        spec: FirewallRuleSpec,
        limit: Int,
    ): AddedFirewallRule {
        val used = ruleRepository.countByVmId(vmId)
        if (used >= limit) {
            throw BusinessException(
                ErrorCode.QUOTA_EXCEEDED,
                details = mapOf("limit" to limit, "used" to used),
                message = "방화벽 규칙은 VM당 ${limit}개까지 추가할 수 있습니다.",
            )
        }
        val existing = firewallRepository.findById(vmId).orElse(null)
        val firewall = existing ?: VmFirewall(vmId)
        firewall.enable()
        firewallRepository.save(firewall)
        val rule = ruleRepository.save(FirewallRule.create(newRuleId(), vmId, spec))
        return AddedFirewallRule(rule, firewallCreated = existing == null, state = stateOf(vmId))
    }

    /** Go 반영에 실패했을 때 [add]를 되돌린다 */
    @Transactional
    fun revertAdd(added: AddedFirewallRule) {
        ruleRepository.deleteById(added.rule.id)
        if (added.firewallCreated) {
            firewallRepository.deleteById(added.rule.vmId)
        }
    }

    /** [excludingRuleId]를 뺀 원하는 상태. 지우기 전에 Go에 먼저 보낼 상태를 만든다 */
    @Transactional(readOnly = true)
    fun state(
        vmId: String,
        excludingRuleId: String? = null,
    ): FirewallState {
        val state = stateOf(vmId)
        return state.copy(rules = state.rules.filter { it.id != excludingRuleId })
    }

    @Transactional
    fun delete(ruleId: String) {
        ruleRepository.deleteById(ruleId)
    }

    private fun stateOf(vmId: String): FirewallState =
        FirewallState(
            enabled = firewallRepository.findById(vmId).map { it.enabled }.orElse(false),
            rules = ruleRepository.findAllByVmIdOrderByCreatedAtAscIdAsc(vmId),
        )

    // id가 이미 있으면 save가 기존 행을 덮어쓰므로 겹치지 않을 때까지 다시 뽑는다
    private fun newRuleId(): String =
        generateSequence { IdGenerator.subResourceId(SubResourceType.FIREWALL_RULE) }
            .first { !ruleRepository.existsById(it) }
}
