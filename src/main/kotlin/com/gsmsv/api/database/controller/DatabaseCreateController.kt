package com.gsmsv.api.database.controller

import com.gsmsv.api.database.dto.CreateDatabaseRequest
import com.gsmsv.api.database.dto.CreateDatabaseResponse
import com.gsmsv.api.database.service.DatabaseProvisionService
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

/** 만드는 일이 끝나기 전에도 항상 202다 (201로 바꾸지 않는다). 접속 정보는 응답에 넣지 않는다 */
@RestController
class DatabaseCreateController(
    private val provisionService: DatabaseProvisionService,
    private val teamLoader: TeamLoader,
    private val teamService: TeamService,
) {
    /** 개인 DB는 요청자 본인 앞으로만 만들 수 있어 별도 권한 판단이 없다 */
    @Authenticated
    @PostMapping("/api/v2/databases")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun createPersonal(
        @CurrentUser user: AuthenticatedUser,
        @Valid @RequestBody request: CreateDatabaseRequest,
    ): CreateDatabaseResponse = provisionService.createPersonal(user, request)

    @RequirePermission(Action.CREATE)
    @PostMapping("/api/v2/teams/{team_id}/databases")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun createTeam(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable("team_id") teamId: String,
        @Valid @RequestBody request: CreateDatabaseRequest,
    ): CreateDatabaseResponse {
        val team = teamLoader.load(user.toActor(), teamId, Action.CREATE, teamService)
        return provisionService.createTeam(user, team, request)
    }
}
