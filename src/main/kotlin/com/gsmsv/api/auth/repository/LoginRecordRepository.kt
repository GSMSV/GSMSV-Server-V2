package com.gsmsv.api.auth.repository

import com.gsmsv.api.auth.domain.LoginCode
import com.gsmsv.api.auth.domain.LoginState
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 로그인 state·임시 코드 저장소. 값은 해시([sha256])로만 저장한다.
 *
 * 소모는 `DELETE ... RETURNING` 한 문장이라 찾기와 지우기가 갈라지지 않는다.
 * 같은 값으로 동시에 두 번 소모해도 한 요청만 행을 받는다. 만료 조건도 같은 문장에 걸어, 정리가 늦어도 만료된 값은 소모되지 않는다.
 */
@Repository
class LoginRecordRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun saveState(
        stateHash: String,
        redirect: String,
        now: Instant,
        expiresAt: Instant,
    ) {
        jdbcTemplate.update(
            "INSERT INTO auth_login_states (state_hash, redirect, expires_at, created_at) VALUES (?, ?, ?, ?)",
            stateHash,
            redirect,
            utc(expiresAt),
            utc(now),
        )
    }

    /** 만료되지 않은 state를 지우면서 돌려준다. 없거나 이미 썼거나 만료됐으면 `null` */
    fun consumeState(
        stateHash: String,
        now: Instant,
    ): LoginState? =
        jdbcTemplate
            .query(
                "DELETE FROM auth_login_states WHERE state_hash = ? AND expires_at > ? RETURNING redirect",
                { rs, _ -> LoginState(rs.getString("redirect")) },
                stateHash,
                utc(now),
            ).firstOrNull()

    fun saveCode(
        codeHash: String,
        userId: String,
        redirect: String,
        now: Instant,
        expiresAt: Instant,
    ) {
        jdbcTemplate.update(
            "INSERT INTO auth_login_codes (code_hash, user_id, redirect, expires_at, created_at) VALUES (?, ?, ?, ?, ?)",
            codeHash,
            userId,
            redirect,
            utc(expiresAt),
            utc(now),
        )
    }

    /** 만료되지 않은 임시 코드를 지우면서 돌려준다. 없거나 이미 썼거나 만료됐으면 `null` */
    fun consumeCode(
        codeHash: String,
        now: Instant,
    ): LoginCode? =
        jdbcTemplate
            .query(
                "DELETE FROM auth_login_codes WHERE code_hash = ? AND expires_at > ? RETURNING user_id, redirect",
                { rs, _ -> LoginCode(rs.getString("user_id"), rs.getString("redirect")) },
                codeHash,
                utc(now),
            ).firstOrNull()

    /** 만료된 행을 지우고 지운 개수(state, 임시 코드)를 돌려준다 */
    fun deleteExpired(now: Instant): Pair<Int, Int> =
        jdbcTemplate.update("DELETE FROM auth_login_states WHERE expires_at <= ?", utc(now)) to
            jdbcTemplate.update("DELETE FROM auth_login_codes WHERE expires_at <= ?", utc(now))

    // pgjdbc는 Instant를 바로 받지 못해 OffsetDateTime으로 바꿔 넘긴다
    private fun utc(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
