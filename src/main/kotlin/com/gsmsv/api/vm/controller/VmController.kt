package com.gsmsv.api.vm.controller

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Authenticated
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.RequirePermission
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.grn.Grn
import com.gsmsv.api.global.grn.IdParser
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.vm.dto.VmDetailResponse
import com.gsmsv.api.vm.dto.VmMetricsResponse
import com.gsmsv.api.vm.dto.VmSummaryResponse
import com.gsmsv.api.vm.service.VmMetricsService
import com.gsmsv.api.vm.service.VmQueryService
import com.gsmsv.api.vm.service.VmResourceReader
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/vms")
class VmController(
    private val loader: ServiceResourceLoader,
    private val vmReader: VmResourceReader,
    private val vmQueryService: VmQueryService,
    private val vmMetricsService: VmMetricsService,
) {
    /** 4.5 VM 목록. `owner`는 `me` 또는 `team-…`이다. 볼 수 없는 팀이면 빈 목록이 아니라 404다 */
    @Authenticated
    @GetMapping
    fun list(
        @CurrentUser user: AuthenticatedUser,
        @RequestParam(required = false) owner: String?,
        @Valid @ModelAttribute page: CursorPageRequest,
    ): CursorPage<VmSummaryResponse> = vmQueryService.list(user.toActor(), parseOwner(user, owner), page)

    /** 4.6 VM 상세. 본인·팀원 전원·관리자 */
    @RequirePermission(Action.VIEW)
    @GetMapping("/{grn}")
    fun get(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
    ): VmDetailResponse {
        val actor = user.toActor()
        val vm = loader.load(actor, grn.value, Action.VIEW, vmReader)
        return vmQueryService.get(actor, vm)
    }

    /** 4.8 VM 사용량 그래프. 본인·팀원 전원·관리자. `period`는 `hour`(기본) 또는 `day`다 */
    @RequirePermission(Action.VIEW)
    @GetMapping("/{grn}/metrics")
    fun metrics(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
        @RequestParam(required = false) period: String?,
    ): VmMetricsResponse {
        val vm = loader.load(user.toActor(), grn.value, Action.VIEW, vmReader)
        return vmMetricsService.get(vm, period)
    }

    private fun parseOwner(
        user: AuthenticatedUser,
        owner: String?,
    ): Owner? =
        when (owner) {
            null -> null
            "me" -> Owner.User(user.userId)
            else -> Owner.Team(IdParser.requireTeamId(owner))
        }
}
