package com.gsmsv.api.database.dto

import com.gsmsv.api.global.response.OwnerResponse
import java.time.Instant

// engine·isolation·status는 계약대로 소문자 문자열로 내보낸다 (예: `mysql`, `active`)

data class DatabaseStorageResponse(
    val usedGb: Double,
)

data class DatabaseSummaryResponse(
    val grn: String,
    val name: String,
    val engine: String,
    val isolation: String,
    val status: String,
    val writeLocked: Boolean,
    val storage: DatabaseStorageResponse,
    val expiresAt: Instant,
)

/** 접속 주소. 비밀번호는 넣지 않는다 (7.5로 따로 받는다) */
data class DatabaseConnectionResponse(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val tls: Boolean,
)

data class DatabaseDetailStorageResponse(
    val usedGb: Double,
    val ownerTotalUsedGb: Double,
    val ownerLimitGb: Double,
)

data class CreatedByResponse(
    val name: String,
)

data class DatabaseResponse(
    val grn: String,
    val id: String,
    val name: String,
    val owner: OwnerResponse,
    val engine: String,
    val isolation: String,
    val status: String,
    val writeLocked: Boolean,
    val connection: DatabaseConnectionResponse,
    val storage: DatabaseDetailStorageResponse,
    val maxConnections: Int,
    val createdBy: CreatedByResponse,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant,
    val permissions: Map<String, Boolean>,
)
