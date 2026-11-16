package com.gsmsv.api.team.dto

import jakarta.validation.constraints.NotBlank

data class CreateTeamRequest(
    @field:NotBlank
    val name: String,
)
