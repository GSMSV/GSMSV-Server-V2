package com.gsmsv.api.global.security

import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccessTokenProviderTest {
    private val now = Instant.parse("2026-11-04T00:00:00Z")
    private val provider = provider(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `발급한 토큰을 검증하면 사용자 id와 역할이 나온다`() {
        val token = provider.issue(USER_ID, Role.ADMIN)

        val user = provider.toAuthentication(provider.decoder.decode(token.value)).principal as AuthenticatedUser

        assertEquals(AuthenticatedUser(USER_ID, Role.ADMIN), user)
        assertEquals(Duration.ofMinutes(15), token.expiresIn)
    }

    @Test
    fun `만료된 토큰은 거절한다`() {
        val token = provider.issue(USER_ID, Role.USER)
        val later = provider(Clock.fixed(now.plus(Duration.ofMinutes(20)), ZoneOffset.UTC))

        assertFailsWith<JwtException> { later.decoder.decode(token.value) }
    }

    @Test
    fun `다른 키로 서명한 토큰은 거절한다`() {
        val other = provider(Clock.fixed(now, ZoneOffset.UTC), secret = encode("another-test-only-key-another-key"))

        assertFailsWith<JwtException> { provider.decoder.decode(other.issue(USER_ID, Role.USER).value) }
    }

    @Test
    fun `서명이 맞아도 클레임이 이 서버 형식이 아니면 거절한다`() {
        val invalidClaims =
            listOf(
                claims(),
                claims().claim("token_use", "refresh"),
                claims().claim("token_use", "access").claim("role", "OWNER"),
                claims().claim("token_use", "access").claim("role", "USER").subject("team-4p8w2k9m1xza"),
                claims().claim("token_use", "access").claim("role", "USER").issuer("datagsm"),
            )
        invalidClaims.forEach { builder ->
            assertFailsWith<JwtException> { provider.decoder.decode(sign(builder.build())) }
        }
    }

    @Test
    fun `서명 키가 32바이트보다 짧거나 Base64가 아니면 기동하지 않는다`() {
        listOf(encode("short-key"), "not base64!").forEach { secret ->
            assertFailsWith<IllegalStateException> { provider(Clock.systemUTC(), secret) }
        }
    }

    private fun claims(): JwtClaimsSet.Builder =
        JwtClaimsSet
            .builder()
            .issuer(AccessTokenProvider.ISSUER)
            .subject(USER_ID)
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofMinutes(15)))

    private fun sign(claims: JwtClaimsSet): String {
        val key = SecretKeySpec(Base64.getDecoder().decode(TEST_SECRET), "HmacSHA256")
        val encoder = NimbusJwtEncoder.withSecretKey(key).build()
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }

    private fun provider(
        clock: Clock,
        secret: String = TEST_SECRET,
    ) = AccessTokenProvider(JwtProperties(secret, Duration.ofMinutes(15)), clock)

    companion object {
        const val USER_ID = "user-4p8w2k9m1xza"

        // 테스트 전용 값이다 ("test-only-key-test-only-key-test"의 Base64). 실제 서버의 키가 아니다
        const val TEST_SECRET = "dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="

        private fun encode(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray())
    }
}
