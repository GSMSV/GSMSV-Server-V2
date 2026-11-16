package com.gsmsv.api.app.dto

import com.gsmsv.api.global.response.OwnerResponse
import java.time.Instant

data class AppSummaryResponse(
    val grn: String,
    val id: String,
    val name: String,
    val owner: OwnerResponse,
    val status: String,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant,
    val permissions: Map<String, Boolean>,
)
