package com.gsmsv.api.database.dto

import java.time.Instant

/** 백업 하나 (7.10). [kind]는 계약대로 소문자(`daily`, `pre_restore`)다 */
data class DatabaseBackupResponse(
    val id: String,
    val kind: String,
    val createdAt: Instant,
    val sizeMb: Double,
    val expiresAt: Instant,
)

data class DatabaseBackupListResponse(
    val items: List<DatabaseBackupResponse>,
)
