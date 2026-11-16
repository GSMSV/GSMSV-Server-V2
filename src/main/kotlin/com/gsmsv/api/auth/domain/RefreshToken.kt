package com.gsmsv.api.auth.domain

import java.time.Instant

class RefreshToken(
    val userId: String,
    val value: String,
    val expiresAt: Instant,
) {
    override fun toString(): String = "RefreshToken(userId=$userId, expiresAt=$expiresAt)"
}
