package com.gsmsv.api.audit

import com.gsmsv.api.TestcontainersConfiguration
import com.gsmsv.api.audit.domain.AuditActorType
import com.gsmsv.api.audit.domain.AuditEventType
import com.gsmsv.api.audit.repository.AuditLogRepository
import com.gsmsv.api.audit.service.AuditRecorder
import com.gsmsv.api.global.authorization.AdminActor
import com.gsmsv.api.global.authorization.SystemActor
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="])
@Tag("integration")
@Transactional
class AuditLogPersistenceTest
    @Autowired
    constructor(
        private val recorder: AuditRecorder,
        private val repository: AuditLogRepository,
    ) {
        @Test
        fun `관리자 동작을 부가 정보와 함께 저장하고 다시 읽는다`() {
            recorder.record(
                AdminActor("user-a1b2c3d4e5f6"),
                AuditEventType.DATABASE_DELETED_RECOVER,
                "grn:team-4p8w2k9m1xza:db-k3m9x2p7q4ta",
                mapOf("original_owner" to "team-4p8w2k9m1xza"),
            )
            repository.flush()

            val saved = repository.findAll().single()
            assertEquals(AuditActorType.ADMIN, saved.actorType)
            assertEquals("team-4p8w2k9m1xza", saved.details["original_owner"])
        }

        @Test
        fun `시스템 주체는 주체 id 없이 저장된다`() {
            recorder.record(SystemActor, AuditEventType.DATABASE_DELETE, "grn:team-4p8w2k9m1xza:db-k3m9x2p7q4ta")
            repository.flush()

            val saved = repository.findAll().single()
            assertEquals(AuditActorType.SYSTEM, saved.actorType)
            assertNull(saved.actorId)
            assertEquals(emptyMap(), saved.details)
        }
    }
