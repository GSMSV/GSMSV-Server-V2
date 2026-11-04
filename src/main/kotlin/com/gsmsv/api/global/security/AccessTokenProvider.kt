package com.gsmsv.api.global.security

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.grn.IdParser
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * 이 서버의 액세스 토큰을 발급하고 검증한다. 로그인 흐름과 개발용 토큰 API가 [issue]를 같이 쓴다.
 *
 * DataGSM 토큰은 로그인할 때 신원 확인에만 쓰고 API 인증에는 쓰지 않는다.
 * 역할(USER/ADMIN)과 토큰 수명을 이 서버가 정해야 하기 때문이다.
 */
@Component
class AccessTokenProvider(
    properties: JwtProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val ttl: Duration = properties.accessTokenTtl
    private val key: SecretKey = decodeKey(properties.secret)
    private val encoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(key))

    val decoder: JwtDecoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().apply {
            setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtTimestampValidator().apply { setClock(clock) },
                    JwtIssuerValidator(ISSUER),
                    ClaimsValidator,
                ),
            )
        }

    fun issue(
        userId: String,
        role: Role,
    ): AccessToken {
        val now = clock.instant()
        val claims =
            JwtClaimsSet
                .builder()
                .issuer(ISSUER)
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(ROLE_CLAIM, role.name)
                .claim(TOKEN_USE_CLAIM, TOKEN_USE_ACCESS)
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return AccessToken(encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue, ttl)
    }

    /** [decoder]가 검증을 마친 토큰만 받으므로 클레임 값은 이미 맞다. 그래도 값이 없으면 401로 끝낸다 */
    fun toAuthentication(jwt: Jwt): AbstractAuthenticationToken {
        val userId = jwt.subject ?: throw InvalidBearerTokenException(INVALID_TOKEN)
        val role =
            Role.entries.firstOrNull { it.name == jwt.getClaimAsString(ROLE_CLAIM) } ?: throw InvalidBearerTokenException(INVALID_TOKEN)
        val user = AuthenticatedUser(userId, role)
        return UsernamePasswordAuthenticationToken.authenticated(user, null, listOf(SimpleGrantedAuthority("ROLE_${role.name}")))
    }

    /** 서명이 맞아도 내용이 이 서버 형식이 아니면 거절한다 (나중에 생길 리프레시 토큰을 액세스 토큰으로 쓰는 일도 막는다) */
    private object ClaimsValidator : OAuth2TokenValidator<Jwt> {
        private val invalid = OAuth2TokenValidatorResult.failure(OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN))

        override fun validate(token: Jwt): OAuth2TokenValidatorResult {
            val valid =
                token.expiresAt != null &&
                    token.getClaimAsString(TOKEN_USE_CLAIM) == TOKEN_USE_ACCESS &&
                    Role.entries.any { it.name == token.getClaimAsString(ROLE_CLAIM) } &&
                    isUserId(token.subject)
            return if (valid) OAuth2TokenValidatorResult.success() else invalid
        }

        private fun isUserId(subject: String?): Boolean =
            subject != null &&
                try {
                    IdParser.requireUserId(subject)
                    true
                } catch (_: BusinessException) {
                    false
                }
    }

    companion object {
        const val ISSUER = "gsmsv"
        private const val ROLE_CLAIM = "role"
        private const val TOKEN_USE_CLAIM = "token_use"
        private const val TOKEN_USE_ACCESS = "access"
        private const val MIN_KEY_BYTES = 32
        private const val INVALID_TOKEN = "액세스 토큰이 올바르지 않습니다."

        // 오류 메시지에 설정값을 넣지 않는다 (비밀값이 로그에 남는다)
        private fun decodeKey(secret: String): SecretKey {
            val bytes =
                try {
                    Base64.getDecoder().decode(secret)
                } catch (_: IllegalArgumentException) {
                    throw IllegalStateException("gsmsv.security.jwt.secret은 Base64 형식이어야 합니다.")
                }
            check(bytes.size >= MIN_KEY_BYTES) { "gsmsv.security.jwt.secret은 $MIN_KEY_BYTES 바이트 이상이어야 합니다." }
            return SecretKeySpec(bytes, "HmacSHA256")
        }
    }
}

data class AccessToken(
    val value: String,
    val expiresIn: Duration,
)
