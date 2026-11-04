package com.gsmsv.api.global.authorization

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RuleTableTest {
    private val memberActions = setOf(Action.VIEW, Action.VIEW_ACCESS_INFO, Action.EXTEND, Action.LEAVE_TEAM)
    private val leaderOnlyActions =
        setOf(
            Action.INVITE_MEMBER,
            Action.REMOVE_MEMBER,
            Action.GRANT_DEVOPS,
            Action.DISBAND_TEAM,
            Action.TRANSFER_LEADER,
            Action.REVOKE_DEVOPS,
        )
    private val adminOnlyActions = setOf(Action.APPROVE_TEAM, Action.APPROVE_DISBAND_TEAM)

    @Test
    fun `팀원은 조회·접속 정보 보기·연장·탈퇴만 할 수 있다`() {
        assertEquals(memberActions, allowedActions(TeamRole.MEMBER))
    }

    @Test
    fun `DevOps는 팀장 전용·관리자 전용 동작을 뺀 모든 동작을 할 수 있다`() {
        assertEquals(Action.entries.toSet() - leaderOnlyActions - adminOnlyActions, allowedActions(TeamRole.DEVOPS))
    }

    @Test
    fun `DevOps는 대상이 본인일 때 DevOps 역할을 회수할 수 있다`() {
        assertEquals(
            Action.entries.toSet() - leaderOnlyActions - adminOnlyActions + Action.REVOKE_DEVOPS,
            allowedActions(TeamRole.DEVOPS, targetsSelf = true),
        )
    }

    @Test
    fun `일반 팀원은 대상이 본인이어도 DevOps 역할을 회수할 수 없다`() {
        assertEquals(memberActions, allowedActions(TeamRole.MEMBER, targetsSelf = true))
    }

    @Test
    fun `팀장은 관리자 전용 동작을 뺀 모든 동작을 할 수 있다`() {
        assertEquals(Action.entries.toSet() - adminOnlyActions, allowedActions(TeamRole.LEADER))
    }

    @Test
    fun `팀 승인 동작은 어떤 팀 역할로도 할 수 없다`() {
        for (role in TeamRole.entries) {
            assertEquals(emptySet(), adminOnlyActions.filter { RuleTable.allows(role, it, targetsSelf = true) }.toSet())
        }
    }

    private fun allowedActions(
        role: TeamRole,
        targetsSelf: Boolean = false,
    ): Set<Action> = Action.entries.filter { RuleTable.allows(role, it, targetsSelf) }.toSet()
}
