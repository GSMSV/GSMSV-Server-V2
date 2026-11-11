package com.gsmsv.api.database.controller

import com.gsmsv.api.database.dto.DatabaseResponse
import com.gsmsv.api.database.dto.DatabaseSummaryResponse
import com.gsmsv.api.database.service.DatabaseApiService
import com.gsmsv.api.database.service.DatabaseResourceReader
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
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/databases")
class DatabaseController(
    private val loader: ServiceResourceLoader,
    private val reader: DatabaseResourceReader,
    private val databaseApiService: DatabaseApiService,
) {
    /** `owner`는 `me` 또는 `team-…`이다. 볼 수 없는 팀이면 빈 목록이 아니라 404다 */
    @Authenticated
    @GetMapping
    fun list(
        @CurrentUser user: AuthenticatedUser,
        @RequestParam(required = false) owner: String?,
        @Valid @ModelAttribute page: CursorPageRequest,
    ): CursorPage<DatabaseSummaryResponse> = databaseApiService.list(user.toActor(), parseOwner(user, owner), page)

    @RequirePermission(Action.VIEW)
    @GetMapping("/{grn}")
    fun get(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
    ): DatabaseResponse {
        val actor = user.toActor()
        val database = loader.load(actor, grn.value, Action.VIEW, reader)
        return databaseApiService.get(actor, database)
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
