package com.gsmsv.api.database.dto

import jakarta.validation.constraints.NotBlank
import java.time.Instant

/** 앞뒤 공백을 지운 뒤 1~30자다. 길이는 service가 검사한다 */
data class RenameDatabaseRequest(
    @field:NotBlank val name: String,
)

data class RenameDatabaseResponse(
    val grn: String,
    val name: String,
)

data class DatabaseExtendResponse(
    val expiresAt: Instant,
)
