package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertSame

class OperationLoaderTest {
    private val teamOperation = FakeOperation("op-7w8k9q0p1a2s", Owner.Team(TEAM))
    private val myOperation = FakeOperation("op-8x9k2q3p4a5s", Owner.User(ME))
    private val otherUserOperation = FakeOperation("op-9y2k3q4p5a6s", Owner.User(OTHER_USER))
    private val reader = FakeOperationReader(teamOperation, myOperation, otherUserOperation)

    private val auditLogger = mockk<AuthorizationAuditLogger>(relaxed = true)

    private fun loader(vararg memberships: Pair<String, Map<String, TeamRole>>) =
        OperationLoader(AuthorizationPolicy(FakeTeamMembershipReader(memberships.toMap())), auditLogger)

    private fun errorOf(block: () -> Unit): ErrorCode = assertThrows<BusinessException> { block() }.errorCode

    @Test
    fun `대상 자원의 팀원이면 역할과 상관없이 조회할 수 있다`() {
        for (role in TeamRole.entries) {
            val loaded = loader(TEAM to mapOf(ME to role)).load(UserActor(ME), teamOperation.id, Action.VIEW, reader)

            assertSame(teamOperation, loaded, "$role")
        }
    }

    @Test
    fun `대상 자원 팀의 팀원이 아니면 404다`() {
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, errorOf { loader().load(UserActor(ME), teamOperation.id, Action.VIEW, reader) })
    }

    @Test
    fun `내 개인 자원의 작업은 볼 수 있고 남의 개인 자원 작업은 404다`() {
        assertSame(myOperation, loader().load(UserActor(ME), myOperation.id, Action.VIEW, reader))
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, errorOf { loader().load(UserActor(ME), otherUserOperation.id, Action.VIEW, reader) })
    }

    @Test
    fun `없는 operation과 볼 수 없는 operation은 같은 404다`() {
        val missing = errorOf { loader().load(UserActor(ME), "op-2b3c4d5e6f7g", Action.VIEW, reader) }
        val hidden = errorOf { loader().load(UserActor(ME), teamOperation.id, Action.VIEW, reader) }

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, missing)
        assertEquals(missing, hidden)
    }

    @Test
    fun `id 형식이 틀리면 DB를 보기 전에 400으로 거절한다`() {
        assertEquals(ErrorCode.INVALID_RESOURCE_ID, errorOf { loader().load(UserActor(ME), "job-7w8k9q0p1a2s", Action.VIEW, reader) })
        assertEquals(0, reader.findByIdCount)
    }

    @Test
    fun `관리자는 통과하고 감사 로그를 남긴다`() {
        val actor = AdminActor(ME)

        assertSame(teamOperation, loader().load(actor, teamOperation.id, Action.VIEW, reader))

        verify { auditLogger.record(actor, Action.VIEW, teamOperation.id, Decision.AllowAsAdmin) }
    }

    private class FakeOperation(
        override val id: String,
        override val targetOwner: Owner,
    ) : OperationRecord

    private class FakeOperationReader(
        vararg operations: FakeOperation,
    ) : OperationReader<FakeOperation> {
        private val operations = operations.toList()
        var findByIdCount = 0
            private set

        override fun findById(id: String): FakeOperation? {
            findByIdCount++
            return operations.find { it.id == id }
        }
    }
}
