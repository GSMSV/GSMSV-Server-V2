package com.gsmsv.api.network.controller

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.RequirePermission
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.SubResourceLoader
import com.gsmsv.api.global.grn.Grn
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.network.dto.CreateFirewallRuleRequest
import com.gsmsv.api.network.dto.CreateFirewallRuleResponse
import com.gsmsv.api.network.dto.FirewallRulePageResponse
import com.gsmsv.api.network.service.FirewallRuleResourceReader
import com.gsmsv.api.network.service.FirewallRuleService
import com.gsmsv.api.vm.service.VmResourceReader
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** VM 방화벽 규칙 (5.14~5.16). 조회는 본인·팀원 전원·관리자, 추가·삭제는 개인 본인 / 팀 팀장·DevOps다 */
@RestController
@RequestMapping("/api/v2/vms/{grn}/firewall-rules")
class FirewallRuleController(
    private val loader: ServiceResourceLoader,
    private val subResourceLoader: SubResourceLoader,
    private val vmReader: VmResourceReader,
    private val ruleReader: FirewallRuleResourceReader,
    private val firewallRuleService: FirewallRuleService,
) {
    @RequirePermission(Action.VIEW)
    @GetMapping
    fun list(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
        @Valid @ModelAttribute page: CursorPageRequest,
    ): FirewallRulePageResponse {
        val vm = loader.load(user.toActor(), grn.value, Action.VIEW, vmReader)
        return firewallRuleService.list(vm, page)
    }

    @RequirePermission(Action.FIREWALL)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
        @Valid @RequestBody request: CreateFirewallRuleRequest,
    ): CreateFirewallRuleResponse {
        val vm = loader.load(user.toActor(), grn.value, Action.FIREWALL, vmReader)
        return firewallRuleService.create(vm, request)
    }

    @RequirePermission(Action.FIREWALL)
    @DeleteMapping("/{fwrule_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
        @PathVariable("fwrule_id") ruleId: String,
    ) {
        val loaded = subResourceLoader.load(user.toActor(), grn.value, ruleId, Action.FIREWALL, vmReader, ruleReader)
        firewallRuleService.delete(loaded.parent, loaded.child)
    }
}
