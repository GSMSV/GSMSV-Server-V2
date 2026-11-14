package com.gsmsv.api.network.dto

import com.gsmsv.api.network.domain.FirewallRule
import jakarta.validation.constraints.Size

/**
 * 5.15 방화벽 규칙 추가. 모든 칸이 선택이고, 빠지면 `accept`·`in`·`tcp`, 포트·출발지는 전부다.
 * 값의 형식은 `FirewallRuleSpec`이 검사한다. 목적지는 받지 않는다 (모르는 칸은 400).
 */
data class CreateFirewallRuleRequest(
    @field:Size(max = 16) val action: String? = null,
    @field:Size(max = 16) val direction: String? = null,
    @field:Size(max = 16) val protocol: String? = null,
    @field:Size(max = 11) val dport: String? = null,
    @field:Size(max = 49) val source: String? = null,
)

data class CreateFirewallRuleResponse(
    val id: String,
)

/** 규칙 하나 (5.14). enum 값은 소문자다 */
data class FirewallRuleResponse(
    val id: String,
    val action: String,
    val direction: String,
    val protocol: String,
    val dport: String?,
    val source: String?,
) {
    companion object {
        fun from(rule: FirewallRule) =
            FirewallRuleResponse(
                id = rule.id,
                action = rule.action.wire,
                direction = rule.direction.wire,
                protocol = rule.protocol.wire,
                dport = rule.dport,
                source = rule.source,
            )
    }
}

data class FirewallRuleLimits(
    val perVm: Int,
)

/** 5.14 방화벽 규칙 목록. [nextCursor]는 마지막 페이지면 null이다 */
data class FirewallRulePageResponse(
    val enabled: Boolean,
    val items: List<FirewallRuleResponse>,
    val limits: FirewallRuleLimits,
    val nextCursor: String?,
)
