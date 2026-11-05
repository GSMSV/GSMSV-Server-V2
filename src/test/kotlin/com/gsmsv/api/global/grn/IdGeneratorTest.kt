package com.gsmsv.api.global.grn

import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals

class IdGeneratorTest {
    @ParameterizedTest
    @EnumSource(ResourceType::class)
    fun `만든 서비스 자원 id는 GRN 파서를 통과한다`(type: ResourceType) {
        val id = IdGenerator.resourceId(type)

        val parsed = GrnParser.parse(GrnParser.format(IdGenerator.teamId(), id), type)

        assertEquals(ResourceId(type, id), parsed)
    }

    @ParameterizedTest
    @EnumSource(SubResourceType::class)
    fun `만든 하위 자원 id는 하위 id 파서를 통과한다`(type: SubResourceType) {
        val id = IdGenerator.subResourceId(type)

        assertEquals(SubResourceId(type, id), IdParser.parseSubResourceId(id, type))
    }

    @RepeatedTest(100)
    fun `만든 사용자·팀 id는 id 파서를 통과한다`() {
        IdParser.requireUserId(IdGenerator.userId())
        IdParser.requireTeamId(IdGenerator.teamId())
    }

    @RepeatedTest(100)
    fun `만든 팀원 id는 계약 형식에 맞는다`() {
        val id = IdGenerator.teamMemberId()

        assertEquals(16, id.length)
        assertEquals("mbr-", id.take(4))
        assertEquals(true, id.drop(4).all { it in IdPattern.ALPHABET })
    }

    @Test
    fun `무작위 부분은 계약의 32글자를 모두 쓰고 그 밖의 글자는 쓰지 않는다`() {
        val used =
            (1..2_000)
                .flatMap { IdGenerator.resourceId(ResourceType.VM).removePrefix("vm-").toList() }
                .toSet()

        assertEquals("0123456789abcdefghjkmnpqrstvwxyz".toSet(), used)
    }

    @Test
    fun `같은 id를 거의 다시 만들지 않는다`() {
        val ids = List(10_000) { IdGenerator.resourceId(ResourceType.VM) }

        assertEquals(ids.size, ids.toSet().size)
    }
}
