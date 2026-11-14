package com.gsmsv.api.auth.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class RefreshTokenRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun save(
        tokenHash: String,
        userId: String,
        createdAt: Instant,
        expiresAt: Instant,
    ) {
        jdbcTemplate.update(
            "INSERT INTO auth_refresh_tokens (token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
            tokenHash,
            userId,
            utc(createdAt),
            utc(expiresAt),
        )
    }

    fun findByHashForUpdate(tokenHash: String): StoredRefreshToken? =
        jdbcTemplate
            .query(
                "SELECT user_id, expires_at FROM auth_refresh_tokens WHERE token_hash = ? FOR UPDATE",
                { rs, _ ->
                    StoredRefreshToken(rs.getString("user_id"), rs.getObject("expires_at", OffsetDateTime::class.java).toInstant())
                },
                tokenHash,
            ).firstOrNull()

    fun delete(tokenHash: String): Int = jdbcTemplate.update("DELETE FROM auth_refresh_tokens WHERE token_hash = ?", tokenHash)

    private fun utc(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}

data class StoredRefreshToken(
    val userId: String,
    val expiresAt: Instant,
)
