package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class PlatformLoaderTest {
    private val auditLogger = mockk<AuthorizationAuditLogger>(relaxed = true)
    private val loader = PlatformLoader(AuthorizationPolicy(FakeTeamMembershipReader()), auditLogger)

    @Test
    fun `관리자는 통과하고 감사 로그를 남긴다`() {
        val actor = AdminActor(ME)

        assertDoesNotThrow { loader.authorize(actor, Action.VIEW_INFRA) }

        verify { auditLogger.record(actor, Action.VIEW_INFRA, "platform", Decision.AllowAsAdmin) }
    }

    @Test
    fun `일반 사용자는 403이고 필요한 역할을 알려 주며 감사 로그를 남긴다`() {
        val actor = UserActor(ME)

        val exception = assertThrows<BusinessException> { loader.authorize(actor, Action.VIEW_INFRA) }

        assertEquals(ErrorCode.ACCESS_DENIED, exception.errorCode)
        assertEquals(mapOf("required_role" to Grant.ADMIN_ONLY.name), exception.details)
        verify { auditLogger.record(actor, Action.VIEW_INFRA, "platform", Decision.Forbidden(Grant.ADMIN_ONLY, null)) }
    }

    @Test
    fun `관리자 전용이 아닌 동작은 자원 없이 판단하지 않는다`() {
        val nonAdminActions = Action.entries.filter { it.grant != Grant.ADMIN_ONLY }

        for (action in nonAdminActions) {
            assertThrows<IllegalArgumentException>("$action") { loader.authorize(AdminActor(ME), action) }
        }
    }
}
