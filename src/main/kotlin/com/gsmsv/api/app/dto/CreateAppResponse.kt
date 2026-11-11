package com.gsmsv.api.app.dto

import com.gsmsv.api.app.domain.App
import com.gsmsv.api.global.grn.GrnParser
import com.gsmsv.api.global.response.OwnerResponse
import java.time.Instant

data class CreateAppResponse(
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
    val envs: List<AppEnvironmentResponse>,
    val services: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val deployments: List<String> = emptyList(),
) {
    companion object {
        fun from(
            app: App,
            ownerName: String,
            creatorName: String,
            permissions: Map<String, Boolean>,
        ) = CreateAppResponse(
            grn = GrnParser.format(app.owner.id, app.id),
            id = app.id,
            name = app.name,
            owner = OwnerResponse.of(app.owner, ownerName),
            status = app.status,
            createdBy = creatorName,
            createdAt = app.createdAt,
            updatedAt = app.updatedAt,
            expiresAt = app.expiresAt,
            permissions = permissions,
            envs = app.environments.map { AppEnvironmentResponse(it.id, it.name) },
        )
    }
}

data class AppEnvironmentResponse(
    val id: String,
    val name: String,
)
