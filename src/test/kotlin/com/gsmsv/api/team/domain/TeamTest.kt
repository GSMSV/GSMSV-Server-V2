package com.gsmsv.api.team.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeamTest {
    @Test
    fun `팀은 승인 대기 상태로 시작하고 승인되면 승인됨으로 바뀐다`() {
        val team = Team(TEAM_ID, "테스트 팀")

        assertEquals(TeamStatus.PENDING, team.status)
        assertFalse(team.approved)

        team.approve()

        assertEquals(TeamStatus.APPROVED, team.status)
        assertTrue(team.approved)
    }

    @Test
    fun `팀을 거절할 때 사유를 보관한다`() {
        val team = Team(TEAM_ID, "테스트 팀")

        team.reject("팀 이름을 확인해 주세요")

        assertEquals(TeamStatus.REJECTED, team.status)
        assertEquals("팀 이름을 확인해 주세요", team.rejectionReason)
        assertFalse(team.approved)
    }

    @Test
    fun `빈 거절 사유는 받지 않는다`() {
        val team = Team(TEAM_ID, "테스트 팀")

        assertFailsWith<IllegalArgumentException> { team.reject(" ") }
    }

    companion object {
        private const val TEAM_ID = "team-c3d4e5f6g7h8"
    }
}
