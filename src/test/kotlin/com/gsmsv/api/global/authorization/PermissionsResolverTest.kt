package com.gsmsv.api.global.authorization

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PermissionsResolverTest {
    private val teamVm = FakeResource("vm-k3m9x2p7q4ta", Owner.Team(TEAM))
    private val myVm = FakeResource("vm-m4n5p6q7r8s9", Owner.User(ME))

    private class CountingMembershipReader(
        role: TeamRole,
    ) : TeamMembershipReader {
        private val delegate = FakeTeamMembershipReader(mapOf(TEAM to mapOf(ME to role)))
        var findRoleCount = 0
            private set

        override fun findRole(
            teamId: String,
            userId: String,
        ): TeamRole? {
            findRoleCount++
            return delegate.findRole(teamId, userId)
        }

        override fun findMemberships(userId: String): Map<String, TeamRole> = delegate.findMemberships(userId)
    }

    private fun resolver(role: TeamRole) =
        PermissionsResolver(AuthorizationPolicy(FakeTeamMembershipReader(mapOf(TEAM to mapOf(ME to role)))))

    @Test
    fun `동작 이름을 키로, 규칙표 판단을 값으로 돌려준다`() {
        val permissions = resolver(TeamRole.MEMBER).resolve(UserActor(ME), teamVm, listOf(Action.DELETE, Action.EXTEND))

        assertEquals(mapOf("delete" to false, "extend" to true), permissions)
        assertEquals(listOf("delete", "extend"), permissions.keys.toList())
    }

    @Test
    fun `로더와 같은 규칙표로 판단한다`() {
        for (role in TeamRole.entries) {
            val permissions = resolver(role).resolve(UserActor(ME), teamVm, Action.entries)
            for (action in Action.entries) {
                assertEquals(RuleTable.allows(role, action), permissions[action.permissionKey], "$role $action")
            }
        }
    }

    @Test
    fun `팀 역할은 동작 수와 상관없이 한 번만 읽는다`() {
        val reader = CountingMembershipReader(TeamRole.LEADER)

        PermissionsResolver(AuthorizationPolicy(reader)).resolve(UserActor(ME), teamVm, Action.entries)

        assertEquals(1, reader.findRoleCount)
    }

    @Test
    fun `관리자는 모든 동작이 true다`() {
        val permissions = resolver(TeamRole.MEMBER).resolve(AdminActor(OTHER_USER), teamVm, Action.entries)

        assertEquals(true, permissions.values.all { it })
    }

    @Test
    fun `개인 자원 소유자는 관리자 전용 동작만 false다`() {
        val permissions = resolver(TeamRole.MEMBER).resolve(UserActor(ME), myVm, listOf(Action.DELETE, Action.APPROVE_TEAM))

        assertEquals(mapOf("delete" to true, "approve_team" to false), permissions)
    }

    @Test
    fun `승인되지 않은 팀이면 승인이 필요한 동작은 false다`() {
        val permissions =
            resolver(TeamRole.LEADER).resolve(UserActor(ME), FakeTeam(TEAM, approved = false), listOf(Action.CREATE, Action.INVITE_MEMBER))

        assertEquals(mapOf("create" to false, "invite_member" to true), permissions)
    }

    @Test
    fun `팀원이 아니면 모두 false다`() {
        val permissions = resolver(TeamRole.LEADER).resolve(UserActor(OTHER_USER), FakeTeam(TEAM), listOf(Action.VIEW, Action.LEAVE_TEAM))

        assertEquals(mapOf("view" to false, "leave_team" to false), permissions)
    }
}
