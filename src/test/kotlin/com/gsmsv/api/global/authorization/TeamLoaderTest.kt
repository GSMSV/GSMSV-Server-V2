package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TeamLoaderTest {
    private val team = FakeTeam(TEAM)
    private val otherTeam = FakeTeam(OTHER_TEAM)
    private val reader = FakeTeamReader(team, otherTeam)
    private val auditLogger = mockk<AuthorizationAuditLogger>(relaxed = true)

    private fun loader(role: TeamRole) =
        TeamLoader(AuthorizationPolicy(FakeTeamMembershipReader(mapOf(TEAM to mapOf(ME to role)))), auditLogger)

    private fun errorOf(block: () -> Unit): ErrorCode = assertThrows<BusinessException> { block() }.errorCode

    @Test
    fun `역할·동작 조합마다 규칙표대로 판단하고 감사 로그를 남긴다`() {
        for (role in TeamRole.entries) {
            val loader = loader(role)
            for (action in Action.entries) {
                val actor = UserActor(ME)
                if (RuleTable.allows(role, action)) {
                    assertSame(team, loader.load(actor, TEAM, action, reader), "$role $action")
                    verify { auditLogger.record(actor, action, TEAM, Decision.ALLOW) }
                } else {
                    assertEquals(ErrorCode.ACCESS_DENIED, errorOf { loader.load(actor, TEAM, action, reader) }, "$role $action")
                    verify { auditLogger.record(actor, action, TEAM, Decision.FORBIDDEN) }
                }
            }
        }
    }

    @Test
    fun `팀원이 아니면 404를 돌려주고 감사 로그를 남긴다`() {
        val actor = UserActor(ME)
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, errorOf { loader(TeamRole.LEADER).load(actor, OTHER_TEAM, Action.VIEW, reader) })
        verify { auditLogger.record(actor, Action.VIEW, OTHER_TEAM, Decision.NOT_FOUND) }
    }

    @Test
    fun `없는 팀이면 404를 돌려주고 감사 로그를 남긴다`() {
        val actor = AdminActor(ME)
        val missing = "team-zzzzzzzzzzzz"
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, errorOf { loader(TeamRole.LEADER).load(actor, missing, Action.VIEW, reader) })
        verify { auditLogger.record(actor, Action.VIEW, missing, Decision.NOT_FOUND) }
    }

    @Test
    fun `관리자는 팀원이 아니어도 통과하고 감사 로그를 남긴다`() {
        val actor = AdminActor(OTHER_USER)
        assertSame(team, loader(TeamRole.MEMBER).load(actor, TEAM, Action.DISBAND_TEAM, reader))
        verify { auditLogger.record(actor, Action.DISBAND_TEAM, TEAM, Decision.ALLOW_AS_ADMIN) }
    }
}
