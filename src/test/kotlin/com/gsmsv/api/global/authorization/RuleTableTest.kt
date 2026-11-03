package com.gsmsv.api.global.authorization

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RuleTableTest {
    private val memberActions = setOf(Action.VIEW, Action.VIEW_ACCESS_INFO, Action.EXTEND)
    private val leaderOnlyActions =
        setOf(
            Action.INVITE_MEMBER,
            Action.REMOVE_MEMBER,
            Action.GRANT_DEVOPS,
            Action.DISBAND_TEAM,
            Action.TRANSFER_LEADER,
        )

    @Test
    fun `팀원은 조회·접속 정보 보기·연장만 할 수 있다`() {
        assertEquals(memberActions, allowedActions(TeamRole.MEMBER))
    }

    @Test
    fun `DevOps는 팀장 전용 동작을 뺀 모든 동작을 할 수 있다`() {
        assertEquals(Action.entries.toSet() - leaderOnlyActions, allowedActions(TeamRole.DEVOPS))
    }

    @Test
    fun `팀장은 모든 동작을 할 수 있다`() {
        assertEquals(Action.entries.toSet(), allowedActions(TeamRole.LEADER))
    }

    private fun allowedActions(role: TeamRole): Set<Action> = Action.entries.filter { RuleTable.allows(role, it) }.toSet()
}
