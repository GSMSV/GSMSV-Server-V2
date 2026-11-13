package com.gsmsv.api.team.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class InviteMemberRequest(
    @field:NotBlank
    @field:Email
    val email: String,
)

data class InviteMemberResponse(
    val message: String = "초대를 보냈습니다.",
)

data class InvitationResponse(
    val id: String,
    val email: String,
    val sentAt: Instant,
    val expiresAt: Instant,
)

data class AcceptInvitationRequest(
    @field:NotBlank
    val token: String,
)

data class AcceptInvitationResponse(
    val team: InvitationTeamResponse,
    val myRole: String = "member",
)

data class InvitationTeamResponse(
    val id: String,
    val name: String,
)
