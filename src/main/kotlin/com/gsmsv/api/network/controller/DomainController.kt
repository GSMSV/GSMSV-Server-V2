package com.gsmsv.api.network.controller

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.RequirePermission
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.grn.Grn
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.network.dto.CreateDomainRequest
import com.gsmsv.api.network.dto.CreateDomainResponse
import com.gsmsv.api.network.dto.DomainListResponse
import com.gsmsv.api.network.service.DomainService
import com.gsmsv.api.vm.service.VmResourceReader
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/vms/{grn}/domains")
class DomainController(
    private val loader: ServiceResourceLoader,
    private val vmReader: VmResourceReader,
    private val domainService: DomainService,
) {
    /** 5.10 HTTPS 도메인 목록. 본인·팀원 전원·관리자 */
    @RequirePermission(Action.VIEW)
    @GetMapping
    fun list(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
    ): DomainListResponse {
        val vm = loader.load(user.toActor(), grn.value, Action.VIEW, vmReader)
        return domainService.list(vm)
    }

    /** 5.11 HTTPS 도메인 연결. 개인 본인 / 팀 팀장·DevOps */
    @RequirePermission(Action.DOMAIN)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
        @Valid @RequestBody request: CreateDomainRequest,
    ): CreateDomainResponse {
        val vm = loader.load(user.toActor(), grn.value, Action.DOMAIN, vmReader)
        // @Valid가 null을 막았다
        return domainService.create(vm, checkNotNull(request.subdomain), checkNotNull(request.internalPort))
    }
}
