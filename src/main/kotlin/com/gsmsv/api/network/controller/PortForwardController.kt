package com.gsmsv.api.network.controller

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.RequirePermission
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.grn.Grn
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.network.dto.CreatePortForwardRequest
import com.gsmsv.api.network.dto.CreatePortForwardResponse
import com.gsmsv.api.network.dto.PortForwardListResponse
import com.gsmsv.api.network.service.PortForwardService
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
@RequestMapping("/api/v2/vms/{grn}/port-forwards")
class PortForwardController(
    private val loader: ServiceResourceLoader,
    private val vmReader: VmResourceReader,
    private val portForwardService: PortForwardService,
) {
    /** 5.6 포트포워딩 목록. 본인·팀원 전원·관리자가 본다 */
    @RequirePermission(Action.VIEW)
    @GetMapping
    fun list(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
    ): PortForwardListResponse {
        val vm = loader.load(user.toActor(), grn.value, Action.VIEW, vmReader)
        return portForwardService.list(vm)
    }

    /** 5.7 커스텀 포트포워딩 추가. 개인 본인 / 팀 팀장·DevOps */
    @RequirePermission(Action.PORT_FORWARD)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
        @Valid @RequestBody request: CreatePortForwardRequest,
    ): CreatePortForwardResponse {
        val vm = loader.load(user.toActor(), grn.value, Action.PORT_FORWARD, vmReader)
        return portForwardService.create(vm, request)
    }
}
