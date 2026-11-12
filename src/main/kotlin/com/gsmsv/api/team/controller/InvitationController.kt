package com.gsmsv.api.team.controller

import com.gsmsv.api.global.authorization.Authenticated
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.team.dto.AcceptInvitationRequest
import com.gsmsv.api.team.dto.AcceptInvitationResponse
import com.gsmsv.api.team.service.TeamInvitationService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/invitations")
class InvitationController(
    private val teamInvitationService: TeamInvitationService,
) {
    @Authenticated
    @PostMapping("/accept")
    fun acceptInvitation(
        @CurrentUser user: AuthenticatedUser,
        @Valid @RequestBody request: AcceptInvitationRequest,
    ): AcceptInvitationResponse = teamInvitationService.accept(user.userId, request.token)
}
