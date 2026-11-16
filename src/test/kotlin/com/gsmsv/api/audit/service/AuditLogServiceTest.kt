package com.gsmsv.api.audit.service

import com.gsmsv.api.audit.domain.AuditActorType
import com.gsmsv.api.audit.domain.AuditEventType
import com.gsmsv.api.audit.domain.AuditLog
import com.gsmsv.api.audit.repository.AuditLogRepository
import com.gsmsv.api.global.authorization.AdminActor
import com.gsmsv.api.global.authorization.SystemActor
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.config.RequestIdFilter
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AuditLogServiceTest {
    private val repository = mockk<AuditLogRepository>(relaxed = true)
    private val service = AuditLogService(repository)
    private val saved = slot<AuditLog>()

    @AfterEach
    fun clearMdc() {
        MDC.remove(RequestIdFilter.MDC_KEY)
    }

    @Test
    fun `일반 사용자의 동작을 주체와 대상과 함께 저장한다`() {
        every { repository.save(capture(saved)) } answers { saved.captured }

        service.record(UserActor(USER_ID), AuditEventType.DATABASE_CREDENTIALS_READ, DB_GRN)

        assertEquals(AuditActorType.USER, saved.captured.actorType)
        assertEquals(USER_ID, saved.captured.actorId)
        assertEquals(AuditEventType.DATABASE_CREDENTIALS_READ, saved.captured.eventType)
        assertEquals(DB_GRN, saved.captured.target)
    }

    @Test
    fun `관리자는 ADMIN으로 저장하고 부가 정보를 함께 남긴다`() {
        every { repository.save(capture(saved)) } answers { saved.captured }

        service.record(
            AdminActor(USER_ID),
            AuditEventType.DATABASE_DELETED_RECOVER,
            DB_GRN,
            mapOf("original_owner" to "team-4p8w2k9m1xza", "new_owner" to "team-7q3m8k2x9pab"),
        )

        assertEquals(AuditActorType.ADMIN, saved.captured.actorType)
        assertEquals("team-4p8w2k9m1xza", saved.captured.details["original_owner"])
    }

    @Test
    fun `시스템 주체는 주체 id 없이 저장한다`() {
        every { repository.save(capture(saved)) } answers { saved.captured }

        service.record(SystemActor, AuditEventType.DATABASE_DELETE, DB_GRN)

        assertEquals(AuditActorType.SYSTEM, saved.captured.actorType)
        assertNull(saved.captured.actorId)
    }

    @Test
    fun `요청 ID가 있으면 함께 저장하고 없으면 비운다`() {
        every { repository.save(capture(saved)) } answers { saved.captured }

        MDC.put(RequestIdFilter.MDC_KEY, "req-abc123")
        service.record(UserActor(USER_ID), AuditEventType.DATABASE_BACKUP_DOWNLOAD, DB_GRN)
        assertEquals("req-abc123", saved.captured.requestId)

        MDC.remove(RequestIdFilter.MDC_KEY)
        service.record(SystemActor, AuditEventType.DATABASE_DELETE, DB_GRN)
        assertNull(saved.captured.requestId)
    }

    @Test
    fun `부가 정보 키가 비밀값이면 저장하지 않는다`() {
        assertFailsWith<IllegalArgumentException> {
            service.record(
                UserActor(USER_ID),
                AuditEventType.DATABASE_PASSWORD_ROTATE,
                DB_GRN,
                mapOf("new_password" to "x"),
            )
        }

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `부가 정보 값에 IP 주소가 있으면 저장하지 않는다`() {
        listOf("10.0.3.17", "fe80::1", "2001:db8:0:0:0:0:0:1").forEach { ip ->
            assertFailsWith<IllegalArgumentException>(ip) {
                service.record(UserActor(USER_ID), AuditEventType.DATABASE_DELETE, DB_GRN, mapOf("host" to ip))
            }
        }

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `시각과 버전 번호는 IP 주소로 보지 않는다`() {
        every { repository.save(capture(saved)) } answers { saved.captured }

        service.record(
            UserActor(USER_ID),
            AuditEventType.DATABASE_BACKUP_RESTORE,
            DB_GRN,
            mapOf("restored_at" to "2026-11-11T12:30:45Z", "backup" to "backup-7qz3m8k2", "version" to "1.2.3"),
        )

        assertEquals(3, saved.captured.details.size)
    }

    private companion object {
        const val USER_ID = "user-a1b2c3d4e5f6"
        const val DB_GRN = "grn:team-4p8w2k9m1xza:db-k3m9x2p7q4ta"
    }
}
