package com.gsmsv.api.database.dto

import java.io.InputStream
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

/** 내려받을 백업 파일 (7.11). [content]는 받는 쪽이 닫는다. 메모리에 올리지 않고 흘려보낸다 */
class DatabaseBackupFile(
    val fileName: String,
    val sizeBytes: Long,
    val content: InputStream,
)
