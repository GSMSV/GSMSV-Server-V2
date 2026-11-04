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
                val load = { loader.load(actor, TEAM, action, reader, targetUserId = OTHER_USER) }
                if (RuleTable.allows(role, action)) {
                    assertSame(team, load(), "$role $action")
                    verify { auditLogger.record(actor, action, TEAM, Decision.Allow) }
                } else {
                    assertEquals(ErrorCode.ACCESS_DENIED, errorOf { load() }, "$role $action")
                    verify { auditLogger.record(actor, action, TEAM, Decision.Forbidden(action.grant, role)) }
                }
            }
        }
    }

    @Test
    fun `팀원이 아니면 404를 돌려주고 감사 로그를 남긴다`() {
        val actor = UserActor(ME)
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, errorOf { loader(TeamRole.LEADER).load(actor, OTHER_TEAM, Action.VIEW, reader) })
        verify { auditLogger.record(actor, Action.VIEW, OTHER_TEAM, Decision.NotFound) }
    }

    @Test
    fun `없는 팀이면 404를 돌려주고 감사 로그를 남긴다`() {
        val actor = AdminActor(ME)
        val missing = "team-zzzzzzzzzzzz"
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, errorOf { loader(TeamRole.LEADER).load(actor, missing, Action.VIEW, reader) })
        verify { auditLogger.record(actor, Action.VIEW, missing, Decision.NotFound) }
    }

    @Test
    fun `관리자는 팀원이 아니어도 통과하고 감사 로그를 남긴다`() {
        val actor = AdminActor(OTHER_USER)
        assertSame(team, loader(TeamRole.MEMBER).load(actor, TEAM, Action.DISBAND_TEAM, reader))
        verify { auditLogger.record(actor, Action.DISBAND_TEAM, TEAM, Decision.AllowAsAdmin) }
    }

    @Test
    fun `팀 승인은 관리자만 할 수 있다`() {
        assertEquals(ErrorCode.ACCESS_DENIED, errorOf { loader(TeamRole.LEADER).load(UserActor(ME), TEAM, Action.APPROVE_TEAM, reader) })
        assertSame(team, loader(TeamRole.MEMBER).load(AdminActor(OTHER_USER), TEAM, Action.APPROVE_TEAM, reader))
    }

    @Test
    fun `DevOps는 본인의 DevOps 역할만 회수할 수 있다`() {
        val loader = loader(TeamRole.DEVOPS)
        assertSame(team, loader.load(UserActor(ME), TEAM, Action.REVOKE_DEVOPS, reader, targetUserId = ME))
        assertEquals(
            ErrorCode.ACCESS_DENIED,
            errorOf { loader.load(UserActor(ME), TEAM, Action.REVOKE_DEVOPS, reader, targetUserId = OTHER_USER) },
        )
    }

    @Test
    fun `DevOps 역할 회수는 대상 사용자 없이 부를 수 없다`() {
        assertThrows<IllegalArgumentException> { loader(TeamRole.LEADER).load(UserActor(ME), TEAM, Action.REVOKE_DEVOPS, reader) }
    }

    @Test
    fun `승인되지 않은 팀은 권한이 있어도 생성할 수 없다`() {
        val pendingReader = FakeTeamReader(FakeTeam(TEAM, approved = false))
        val actor = UserActor(ME)

        assertEquals(ErrorCode.TEAM_NOT_APPROVED, errorOf { loader(TeamRole.LEADER).load(actor, TEAM, Action.CREATE, pendingReader) })
        verify { auditLogger.record(actor, Action.CREATE, TEAM, Decision.TeamNotApproved) }
        assertEquals(
            ErrorCode.TEAM_NOT_APPROVED,
            errorOf { loader(TeamRole.LEADER).load(AdminActor(OTHER_USER), TEAM, Action.CREATE, pendingReader) },
        )
    }

    @Test
    fun `승인되지 않은 팀도 초대와 팀장 넘기기는 할 수 있다`() {
        val pendingTeam = FakeTeam(TEAM, approved = false)
        val pendingReader = FakeTeamReader(pendingTeam)
        val loader = loader(TeamRole.LEADER)

        assertSame(pendingTeam, loader.load(UserActor(ME), TEAM, Action.INVITE_MEMBER, pendingReader))
        assertSame(pendingTeam, loader.load(UserActor(ME), TEAM, Action.TRANSFER_LEADER, pendingReader))
    }

    @Test
    fun `승인되지 않은 팀이어도 역할이 모자라면 권한 403이 먼저다`() {
        val pendingReader = FakeTeamReader(FakeTeam(TEAM, approved = false))
        assertEquals(ErrorCode.ACCESS_DENIED, errorOf { loader(TeamRole.MEMBER).load(UserActor(ME), TEAM, Action.CREATE, pendingReader) })
    }

    @Test
    fun `팀 id나 대상 사용자 id 형식이 틀리면 조회 전에 400을 돌려준다`() {
        val loader = loader(TeamRole.LEADER)
        assertEquals(ErrorCode.INVALID_RESOURCE_ID, errorOf { loader.load(UserActor(ME), "team-1", Action.VIEW, reader) })
        assertEquals(ErrorCode.INVALID_RESOURCE_ID, errorOf { loader.load(UserActor(ME), "vm-k3m9x2p7q4ta", Action.VIEW, reader) })
        assertEquals(
            ErrorCode.INVALID_RESOURCE_ID,
            errorOf { loader.load(UserActor(ME), TEAM, Action.REVOKE_DEVOPS, reader, targetUserId = "user-1") },
        )
        verify(exactly = 0) { auditLogger.record(any(), any(), any(), any()) }
    }
}
