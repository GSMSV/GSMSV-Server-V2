package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.ResourceType
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ServiceResourceLoaderTest {
    private val teamVm = FakeResource("vm-k3m9x2p7q4ta", Owner.Team(TEAM))
    private val myVm = FakeResource("vm-m4n5p6q7r8s9", Owner.User(ME))
    private val otherUserVm = FakeResource("vm-t2v3w4x5y6z7", Owner.User(OTHER_USER))
    private val otherTeamVm = FakeResource("vm-a2b3c4d5e6f7", Owner.Team(OTHER_TEAM))
    private val reader = FakeResourceReader(teamVm, myVm, otherUserVm, otherTeamVm)

    private val auditLogger = mockk<AuthorizationAuditLogger>(relaxed = true)

    private fun loader(vararg memberships: Pair<String, Map<String, TeamRole>>) =
        ServiceResourceLoader(AuthorizationPolicy(FakeTeamMembershipReader(memberships.toMap())), auditLogger)

    private fun loaderWithRole(role: TeamRole) = loader(TEAM to mapOf(ME to role))

    private fun grn(resource: FakeResource) = "grn:${resource.owner.id}:${resource.id}"

    private fun errorOf(block: () -> Unit): ErrorCode = assertThrows<BusinessException> { block() }.errorCode

    @Test
    fun `팀 자원은 역할·동작 조합마다 규칙표대로 허용하거나 403을 돌려준다`() {
        for (role in TeamRole.entries) {
            val loader = loaderWithRole(role)
            for (action in Action.entries) {
                val load = { loader.load(UserActor(ME), grn(teamVm), action, reader) }
                if (RuleTable.allows(role, action)) {
                    assertSame(teamVm, load(), "$role $action")
                } else {
                    assertEquals(ErrorCode.ACCESS_DENIED, errorOf { load() }, "$role $action")
                }
            }
        }
    }

    @Test
    fun `팀원이 아니면 모든 동작에 404를 돌려준다`() {
        val loader = loaderWithRole(TeamRole.LEADER)
        for (action in Action.entries) {
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, errorOf { loader.load(UserActor(ME), grn(otherTeamVm), action, reader) })
        }
    }

    @Test
    fun `개인 자원 소유자는 모든 동작을 할 수 있다`() {
        val loader = loader()
        for (action in Action.entries) {
            assertSame(myVm, loader.load(UserActor(ME), grn(myVm), action, reader))
        }
    }

    @Test
    fun `남의 개인 자원은 모든 동작에 404를 돌려준다`() {
        val loader = loader()
        for (action in Action.entries) {
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, errorOf { loader.load(UserActor(ME), grn(otherUserVm), action, reader) })
        }
    }

    @Test
    fun `관리자는 모든 동작이 허용되고 감사 로그를 남긴다`() {
        val loader = loader()
        for (action in Action.entries) {
            val actor = AdminActor(ME)
            assertSame(otherTeamVm, loader.load(actor, grn(otherTeamVm), action, reader))
            verify { auditLogger.record(actor, action, grn(otherTeamVm), Decision.ALLOW_AS_ADMIN) }
        }
    }

    @Test
    fun `SYSTEM은 모든 동작이 허용되고 감사 로그를 남기지 않는다`() {
        val loader = loader()
        for (action in Action.entries) {
            assertSame(otherUserVm, loader.load(SystemActor, grn(otherUserVm), action, reader))
        }
        verify(exactly = 0) { auditLogger.record(any(), any(), any(), any()) }
    }

    @Test
    fun `DB 조회는 요청마다 한 번만 한다`() {
        loaderWithRole(TeamRole.MEMBER).load(UserActor(ME), grn(teamVm), Action.VIEW, reader)
        assertEquals(1, reader.findByIdCount)
    }

    @Test
    fun `GRN의 소유자 칸이 DB와 다르면 관리자여도 404를 돌려준다`() {
        val forged = "grn:$ME:${teamVm.id}"
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, errorOf { loader().load(AdminActor(ME), forged, Action.VIEW, reader) })
        assertEquals(
            ErrorCode.RESOURCE_NOT_FOUND,
            errorOf { loaderWithRole(TeamRole.LEADER).load(UserActor(ME), forged, Action.VIEW, reader) },
        )
    }

    @Test
    fun `없는 자원이면 404를 돌려준다`() {
        assertEquals(
            ErrorCode.RESOURCE_NOT_FOUND,
            errorOf { loader().load(AdminActor(ME), "grn:$TEAM:vm-z9y8x7w6v5t4", Action.VIEW, reader) },
        )
    }

    @Test
    fun `GRN 형식이 틀리면 400을 돌려준다`() {
        assertEquals(ErrorCode.INVALID_GRN, errorOf { loader().load(AdminActor(ME), "grn:$TEAM:vm-1", Action.VIEW, reader) })
    }

    @Test
    fun `엔드포인트와 자원 종류가 다르면 400을 돌려준다`() {
        val fnGrn = "grn:$TEAM:fn-k3m9x2p7q4ta"
        assertEquals(
            ErrorCode.GRN_RESOURCE_TYPE_MISMATCH,
            errorOf { loader().load(AdminActor(ME), fnGrn, Action.VIEW, reader) },
        )
    }

    @Test
    fun `listVisible은 내 개인 자원과 속한 팀의 자원만 돌려준다`() {
        val visible = loaderWithRole(TeamRole.MEMBER).listVisible(UserActor(ME), reader)
        assertEquals(setOf(teamVm, myVm), visible.toSet())
    }

    @Test
    fun `listVisible은 관리자에게 전체를 돌려주고 감사 로그를 남긴다`() {
        val actor = AdminActor(ME)
        assertEquals(4, loader().listVisible(actor, reader).size)
        verify { auditLogger.record(actor, Action.VIEW, "list:${ResourceType.VM.prefix}", Decision.ALLOW_AS_ADMIN) }
    }

    @Test
    fun `listVisible은 SYSTEM에게 전체를 돌려준다`() {
        assertEquals(4, loader().listVisible(SystemActor, reader).size)
    }
}
