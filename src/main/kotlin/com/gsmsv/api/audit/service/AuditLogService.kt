package com.gsmsv.api.audit.service

import com.gsmsv.api.audit.domain.AuditActorType
import com.gsmsv.api.audit.domain.AuditEventType
import com.gsmsv.api.audit.domain.AuditLog
import com.gsmsv.api.audit.repository.AuditLogRepository
import com.gsmsv.api.global.authorization.Actor
import com.gsmsv.api.global.authorization.AdminActor
import com.gsmsv.api.global.authorization.SystemActor
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.config.RequestIdFilter
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuditLogService(
    private val auditLogRepository: AuditLogRepository,
) : AuditRecorder {
    @Transactional
    override fun record(
        actor: Actor,
        eventType: AuditEventType,
        target: String,
        details: Map<String, String>,
    ) {
        AuditDetailsGuard.verify(details)
        val (actorType, actorId) = describe(actor)
        auditLogRepository.save(
            AuditLog(
                // 요청 밖(스케줄러)에서는 요청 ID가 없다
                requestId = MDC.get(RequestIdFilter.MDC_KEY),
                actorType = actorType,
                actorId = actorId,
                eventType = eventType,
                target = target,
                details = details,
            ),
        )
    }

    private fun describe(actor: Actor): Pair<AuditActorType, String?> =
        when (actor) {
            is UserActor -> AuditActorType.USER to actor.userId
            is AdminActor -> AuditActorType.ADMIN to actor.userId
            SystemActor -> AuditActorType.SYSTEM to null
        }
}
