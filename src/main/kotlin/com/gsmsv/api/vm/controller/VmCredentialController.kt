package com.gsmsv.api.vm.controller

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.RequirePermission
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.grn.Grn
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.vm.dto.VmCredentialResponse
import com.gsmsv.api.vm.service.VmCredentialService
import com.gsmsv.api.vm.service.VmResourceReader
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/vms/{grn}")
class VmCredentialController(
    private val loader: ServiceResourceLoader,
    private val vmReader: VmResourceReader,
    private val credentialService: VmCredentialService,
) {
    /** 4.7 VM 초기 비밀번호. 본인·팀원 전원·관리자. 상세(4.6)와 나눠 볼 때만 감사 로그를 남긴다 */
    @RequirePermission(Action.VIEW_ACCESS_INFO)
    @GetMapping("/credentials")
    fun credentials(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
    ): ResponseEntity<VmCredentialResponse> {
        val actor = user.toActor()
        val vm = loader.load(actor, grn.value, Action.VIEW_ACCESS_INFO, vmReader)
        // 비밀번호가 들어 있으므로 중간 저장소에 남지 않게 한다
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(credentialService.credentials(actor, vm))
    }
}
