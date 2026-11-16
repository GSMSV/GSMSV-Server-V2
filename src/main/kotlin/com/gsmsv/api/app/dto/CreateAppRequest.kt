package com.gsmsv.api.app.dto

import jakarta.validation.constraints.NotBlank

data class CreateAppRequest(
    @field:NotBlank val name: String,
)
