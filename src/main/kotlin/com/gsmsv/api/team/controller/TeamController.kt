package com.gsmsv.api.team.controller

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Authenticated
import com.gsmsv.api.global.authorization.PermissionsResolver
import com.gsmsv.api.global.authorization.RequirePermission
import com.gsmsv.api.global.authorization.TeamLoader
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.team.dto.CreateTeamRequest
import com.gsmsv.api.team.dto.CreateTeamResponse
import com.gsmsv.api.team.dto.InvitationResponse
import com.gsmsv.api.team.dto.InviteMemberRequest
import com.gsmsv.api.team.dto.InviteMemberResponse
import com.gsmsv.api.team.dto.TeamDetailResponse
import com.gsmsv.api.team.dto.TeamSummaryResponse
import com.gsmsv.api.team.service.TeamApiService
import com.gsmsv.api.team.service.TeamInvitationService
import com.gsmsv.api.team.service.TeamService
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

@RestController
@RequestMapping("/api/v2/teams")
class TeamController(
    private val teamApiService: TeamApiService,
    private val teamService: TeamService,
    private val teamLoader: TeamLoader,
    private val permissionsResolver: PermissionsResolver,
    private val teamInvitationService: TeamInvitationService,
) {
    @RequirePermission(Action.INVITE_MEMBER)
    @PostMapping("/{team_id}/invitations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun inviteMember(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable("team_id") teamId: String,
        @Valid @RequestBody request: InviteMemberRequest,
    ): InviteMemberResponse {
        teamInvitationService.invite(user.toActor(), teamId, request.email)
        return InviteMemberResponse()
    }

    @RequirePermission(Action.INVITE_MEMBER)
    @GetMapping("/{team_id}/invitations")
    fun listInvitations(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable("team_id") teamId: String,
        @Valid @ModelAttribute pageRequest: CursorPageRequest,
    ): CursorPage<InvitationResponse> = teamInvitationService.list(user.toActor(), teamId, pageRequest)

    @RequirePermission(Action.INVITE_MEMBER)
    @DeleteMapping("/{team_id}/invitations/{invitation_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelInvitation(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable("team_id") teamId: String,
        @PathVariable("invitation_id") invitationId: String,
    ) {
        teamInvitationService.cancel(user.toActor(), teamId, invitationId)
    }

    @Authenticated
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createTeam(
        @CurrentUser user: AuthenticatedUser,
        @Valid @RequestBody request: CreateTeamRequest,
    ): CreateTeamResponse = teamApiService.createTeam(user.userId, request.name)

    @Authenticated
    @GetMapping
    fun listMyTeams(
        @CurrentUser user: AuthenticatedUser,
        @Valid @ModelAttribute pageRequest: CursorPageRequest,
    ): CursorPage<TeamSummaryResponse> = teamApiService.listMyTeams(user.userId, pageRequest)

    @RequirePermission(Action.VIEW)
    @GetMapping("/{team_id}")
    fun getTeam(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable("team_id") teamId: String,
    ): TeamDetailResponse {
        val actor = user.toActor()
        val team = teamLoader.load(actor, teamId, Action.VIEW, teamService)
        val permissions = permissionsResolver.resolve(actor, team, TEAM_DETAIL_ACTIONS)
        return teamApiService.getTeamDetail(user.userId, team, permissions)
    }

    companion object {
        private val TEAM_DETAIL_ACTIONS =
            listOf(
                Action.INVITE_MEMBER,
                Action.REMOVE_MEMBER,
                Action.GRANT_DEVOPS,
                Action.TRANSFER_LEADER,
                Action.DISBAND_TEAM,
                Action.LEAVE_TEAM,
                Action.RESUBMIT_TEAM,
            )
    }
}
