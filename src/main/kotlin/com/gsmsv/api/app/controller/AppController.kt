package com.gsmsv.api.app.controller

import com.gsmsv.api.app.dto.CreateAppRequest
import com.gsmsv.api.app.dto.CreateAppResponse
import com.gsmsv.api.app.service.AppCommandService
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Authenticated
import com.gsmsv.api.global.authorization.RequirePermission
import com.gsmsv.api.global.authorization.TeamLoader
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.team.service.TeamService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class AppController(
    private val appCommandService: AppCommandService,
    private val teamLoader: TeamLoader,
    private val teamService: TeamService,
) {
    @Authenticated
    @PostMapping("/api/v2/apps")
    @ResponseStatus(HttpStatus.CREATED)
    fun createPersonalApp(
        @CurrentUser user: AuthenticatedUser,
        @Valid @RequestBody request: CreateAppRequest,
    ): CreateAppResponse = appCommandService.createPersonalApp(user, request.name)

    @RequirePermission(Action.CREATE)
    @PostMapping("/api/v2/teams/{team_id}/apps")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTeamApp(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable("team_id") teamId: String,
        @Valid @RequestBody request: CreateAppRequest,
    ): CreateAppResponse {
        val team = teamLoader.load(user.toActor(), teamId, Action.CREATE, teamService)
        return appCommandService.createTeamApp(user, team, request.name)
    }
}
