package com.gsmsv.api.global.authorization

import io.github.oshai.kotlinlogging.KotlinLogging

/** 권한 감사 로그. 관리자 통과와 팀 전용 로더의 모든 판단을 남긴다. */
class AuthorizationAuditLogger {
    private val log = KotlinLogging.logger(AUDIT_LOGGER_NAME)

    fun record(
        actor: Actor,
        action: Action,
        target: String,
        decision: Decision,
    ) {
        log.info { "actor=${describe(actor)} action=$action target=$target decision=$decision" }
    }

    private fun describe(actor: Actor): String =
        when (actor) {
            is UserActor -> "user:${actor.userId}"
            is AdminActor -> "admin:${actor.userId}"
            SystemActor -> "system"
        }

    companion object {
        const val AUDIT_LOGGER_NAME = "audit.authorization"
    }
}
