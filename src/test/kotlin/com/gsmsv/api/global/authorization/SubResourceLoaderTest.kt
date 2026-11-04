package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.ResourceType
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SubResourceLoaderTest {
    private val vm = FakeResource("vm-k3m9x2p7q4ta", Owner.Team(TEAM))
    private val otherVm = FakeResource("vm-a2b3c4d5e6f7", Owner.Team(TEAM))
    private val parentReader = FakeResourceReader(vm, otherVm)

    private val snapshot = FakeSubResource("snap-a1b2c3d4", vm.id)
    private val otherVmSnapshot = FakeSubResource("snap-e5f6g7h8", otherVm.id)
    private val childReader = FakeSubResourceReader(snapshot, otherVmSnapshot)

    private val vmGrn = "grn:$TEAM:${vm.id}"

    private fun loader(role: TeamRole): SubResourceLoader {
        val policy = AuthorizationPolicy(FakeTeamMembershipReader(mapOf(TEAM to mapOf(ME to role))))
        return SubResourceLoader(ServiceResourceLoader(policy, mockk(relaxed = true)))
    }

    private fun errorOf(block: () -> Unit): ErrorCode = assertThrows<BusinessException> { block() }.errorCode

    @Test
    fun `부모가 통과하고 하위 자원의 부모가 같으면 둘 다 돌려준다`() {
        val loaded = loader(TeamRole.DEVOPS).load(UserActor(ME), vmGrn, snapshot.id, Action.SNAPSHOT, parentReader, childReader)
        assertSame(vm, loaded.parent)
        assertSame(snapshot, loaded.child)
    }

    @Test
    fun `하위 자원의 실제 부모가 요청의 부모와 다르면 404를 돌려준다`() {
        assertEquals(
            ErrorCode.RESOURCE_NOT_FOUND,
            errorOf {
                loader(TeamRole.LEADER).load(UserActor(ME), vmGrn, otherVmSnapshot.id, Action.VIEW, parentReader, childReader)
            },
        )
    }

    @Test
    fun `하위 자원이 없으면 404를 돌려준다`() {
        assertEquals(
            ErrorCode.RESOURCE_NOT_FOUND,
            errorOf { loader(TeamRole.LEADER).load(UserActor(ME), vmGrn, "snap-zzzzzzzz", Action.VIEW, parentReader, childReader) },
        )
    }

    @Test
    fun `하위 자원 표기로도 불러온다`() {
        val loaded = loader(TeamRole.MEMBER).load(UserActor(ME), "$vmGrn/${snapshot.id}", Action.VIEW, parentReader, childReader)
        assertSame(vm, loaded.parent)
        assertSame(snapshot, loaded.child)
    }

    @Test
    fun `하위 자원 표기의 형식이 틀리면 400을 돌려준다`() {
        assertEquals(
            ErrorCode.INVALID_GRN,
            errorOf { loader(TeamRole.LEADER).load(UserActor(ME), "$vmGrn/snap-1", Action.VIEW, parentReader, childReader) },
        )
    }

    @Test
    fun `하위 id 형식이 틀리면 부모를 찾기 전에 400을 돌려준다`() {
        assertEquals(
            ErrorCode.INVALID_RESOURCE_ID,
            errorOf { loader(TeamRole.LEADER).load(UserActor(ME), vmGrn, "snap-1", Action.VIEW, parentReader, childReader) },
        )
        assertEquals(0, parentReader.findByIdCount)
    }

    @Test
    fun `하위 id 종류가 엔드포인트와 다르면 400을 돌려준다`() {
        assertEquals(
            ErrorCode.GRN_RESOURCE_TYPE_MISMATCH,
            errorOf { loader(TeamRole.LEADER).load(UserActor(ME), vmGrn, "port-5q8w3n7z", Action.VIEW, parentReader, childReader) },
        )
    }

    @Test
    fun `부모 종류에 붙을 수 없는 하위 자원 리더로는 부를 수 없다`() {
        val fnParentReader = FakeResourceReader(type = ResourceType.FUNCTION)
        assertThrows<IllegalArgumentException> {
            loader(TeamRole.LEADER).load(UserActor(ME), vmGrn, snapshot.id, Action.VIEW, fnParentReader, childReader)
        }
    }

    @Test
    fun `부모에 대한 권한이 없으면 하위 자원을 찾기 전에 403을 돌려준다`() {
        assertEquals(
            ErrorCode.ACCESS_DENIED,
            errorOf { loader(TeamRole.MEMBER).load(UserActor(ME), vmGrn, snapshot.id, Action.SNAPSHOT, parentReader, childReader) },
        )
    }
}
