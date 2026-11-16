package com.gsmsv.api.monitoring.controller

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.PlatformLoader
import com.gsmsv.api.global.authorization.RequirePermission
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.grn.IdParser
import com.gsmsv.api.global.grn.ResourceType
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.monitoring.dto.AdminResourceFilterRequest
import com.gsmsv.api.monitoring.dto.AdminResourceResponse
import com.gsmsv.api.monitoring.service.AdminResourceFilter
import com.gsmsv.api.monitoring.service.AdminResourceService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/admin/resources")
class AdminResourceController(
    private val platformLoader: PlatformLoader,
    private val adminResourceService: AdminResourceService,
) {
    /** 관리자 전체 자원 목록 (11.14). 관리자가 아니면 403 `ACCESS_DENIED` */
    @RequirePermission(Action.VIEW_INFRA)
    @GetMapping
    fun list(
        @CurrentUser user: AuthenticatedUser,
        @Valid @ModelAttribute filter: AdminResourceFilterRequest,
        @Valid @ModelAttribute page: CursorPageRequest,
    ): CursorPage<AdminResourceResponse> {
        val actor = user.toActor()
        platformLoader.authorize(actor, Action.VIEW_INFRA)
        val resourceFilter =
            AdminResourceFilter(
                type = filter.type?.let(ResourceType::fromPrefix),
                node = filter.node,
                owner = filter.owner?.let(::parseOwner),
            )
        return adminResourceService.list(actor, resourceFilter, page)
    }

    /** @throws BusinessException 사용자·팀 id 형식이 아니면 400 `INVALID_RESOURCE_ID` */
    private fun parseOwner(owner: String): Owner =
        try {
            Owner.User(IdParser.requireUserId(owner))
        } catch (_: BusinessException) {
            Owner.Team(IdParser.requireTeamId(owner))
        }
}
