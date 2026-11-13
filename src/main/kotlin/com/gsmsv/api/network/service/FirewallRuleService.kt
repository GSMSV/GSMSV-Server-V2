package com.gsmsv.api.network.service

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.response.Cursor
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.infra.firewall.FirewallInfraClient
import com.gsmsv.api.infra.firewall.VmFirewallRule
import com.gsmsv.api.network.domain.FirewallRule
import com.gsmsv.api.network.domain.FirewallRuleSpec
import com.gsmsv.api.network.dto.CreateFirewallRuleRequest
import com.gsmsv.api.network.dto.CreateFirewallRuleResponse
import com.gsmsv.api.network.dto.FirewallRuleLimits
import com.gsmsv.api.network.dto.FirewallRulePageResponse
import com.gsmsv.api.network.dto.FirewallRuleResponse
import com.gsmsv.api.network.repository.FirewallSyncLock
import com.gsmsv.api.vm.domain.Vm
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * VM 방화벽 규칙 목록·추가·삭제 (5.14~5.16). 로더가 돌려준 [Vm]과 [FirewallRule]을 받으므로 권한 판단은 끝난 상태다.
 *
 * 추가·삭제는 VM마다 차례 잠금([FirewallSyncLock]) 안에서 DB를 바꾸고 원하는 상태 전체를 Go에 보낸다.
 * Go 반영에 실패하면 DB를 바꾸기 전으로 되돌리고 그 오류(503 등)를 그대로 돌려준다.
 */
@Service
class FirewallRuleService(
    private val store: FirewallRuleStore,
    private val syncLock: FirewallSyncLock,
    private val infraClient: FirewallInfraClient,
) {
    // 목록은 Go에 묻지 않고 DB 값만 읽는다 (원하는 상태의 원천은 DB다). 정렬은 Go에 보내는 순서와 같은 만든 순서다
    @Transactional(readOnly = true)
    fun list(
        vm: Vm,
        page: CursorPageRequest,
    ): FirewallRulePageResponse {
        val cursor = page.decodedCursor()
        val state = store.state(vm.id)
        val rows =
            state.rules
                .filter { cursor == null || isAfter(it, cursor) }
                .take(page.limit + 1)
        val result = CursorPage.of(rows, page.limit, { Cursor(it.createdAt, it.id) }, FirewallRuleResponse::from)
        return FirewallRulePageResponse(
            enabled = state.enabled,
            items = result.items,
            limits = FirewallRuleLimits(PER_VM_LIMIT),
            nextCursor = result.nextCursor,
        )
    }

    fun create(
        vm: Vm,
        request: CreateFirewallRuleRequest,
    ): CreateFirewallRuleResponse {
        val spec = FirewallRuleSpec.of(request.action, request.direction, request.protocol, request.dport, request.source)
        val internalIp = requireInternalIp(vm)
        return syncLock.withLock(vm.id) {
            val added = store.add(vm.id, spec, PER_VM_LIMIT)
            try {
                sync(vm.id, internalIp, added.state)
            } catch (e: BusinessException) {
                store.revertAdd(added)
                throw e
            }
            CreateFirewallRuleResponse(added.rule.id)
        }
    }

    // 지울 규칙을 뺀 상태를 Go에 먼저 반영하고 DB에서 지운다. 반영에 실패하면 DB는 그대로다. 방화벽은 켠 채로 둔다
    fun delete(
        vm: Vm,
        rule: FirewallRule,
    ) {
        val internalIp = requireInternalIp(vm)
        syncLock.withLock(vm.id) {
            sync(vm.id, internalIp, store.state(vm.id, excludingRuleId = rule.id))
            store.delete(rule.id)
        }
    }

    private fun sync(
        vmId: String,
        internalIp: String,
        state: FirewallState,
    ) {
        infraClient.syncVmFirewall(vmId, internalIp, state.enabled, state.rules.map(::toInfraRule))
    }

    private fun toInfraRule(rule: FirewallRule) =
        VmFirewallRule(
            id = rule.id,
            action = rule.action.wire,
            direction = rule.direction.wire,
            protocol = rule.protocol.wire,
            dport = rule.dport,
            source = rule.source,
        )

    // 목적지를 VM 내부 IP로 강제하므로 IP가 없으면 규칙을 반영할 수 없다
    private fun requireInternalIp(vm: Vm): String =
        vm.internalIp ?: throw BusinessException(ErrorCode.CONFLICT, message = "VM에 내부 IP가 아직 없습니다.")

    private fun isAfter(
        rule: FirewallRule,
        cursor: Cursor,
    ): Boolean = rule.createdAt > cursor.createdAt || (rule.createdAt == cursor.createdAt && rule.id > cursor.id)

    companion object {
        /** VM당 규칙 수 상한. 계약에 값이 없어 이슈 #85의 제안값을 쓴다 */
        const val PER_VM_LIMIT = 50
    }
}
