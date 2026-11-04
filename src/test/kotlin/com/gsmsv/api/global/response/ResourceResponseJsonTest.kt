package com.gsmsv.api.global.response

import com.gsmsv.api.global.authorization.Owner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals

@JsonTest
class ResourceResponseJsonTest(
    @Autowired private val jsonMapper: JsonMapper,
) {
    enum class SampleStatus { RUNNING }

    // 도메인 응답이 공통 필드를 구현하는 방식을 그대로 흉내 낸다
    data class SampleResponse(
        override val grn: String,
        override val id: String,
        override val name: String,
        override val owner: OwnerResponse,
        override val status: SampleStatus,
        override val createdBy: String,
        override val createdAt: Instant,
        override val updatedAt: Instant,
        override val expiresAt: Instant?,
        override val permissions: Map<String, Boolean>,
    ) : ResourceResponse

    private val sample =
        SampleResponse(
            grn = "grn:team-aaaaaaaaaaaa:vm-k3m9x2p7q4ta",
            id = "vm-k3m9x2p7q4ta",
            name = "web",
            owner = OwnerResponse.of(Owner.Team("team-aaaaaaaaaaaa"), "개발팀"),
            status = SampleStatus.RUNNING,
            createdBy = "홍길동",
            createdAt = Instant.parse("2026-11-04T01:02:03Z"),
            updatedAt = Instant.parse("2026-11-04T01:02:04Z"),
            expiresAt = null,
            permissions = mapOf("delete" to false, "extend" to true),
        )

    @Test
    fun `공통 필드를 snake_case로 쓰고 만료가 없으면 null로 쓴다`() {
        val json = jsonMapper.writeValueAsString(sample)

        assertEquals(
            """{"grn":"grn:team-aaaaaaaaaaaa:vm-k3m9x2p7q4ta","id":"vm-k3m9x2p7q4ta","name":"web",""" +
                """"owner":{"type":"team","id":"team-aaaaaaaaaaaa","name":"개발팀"},"status":"RUNNING",""" +
                """"created_by":"홍길동","created_at":"2026-11-04T01:02:03Z","updated_at":"2026-11-04T01:02:04Z",""" +
                """"expires_at":null,"permissions":{"delete":false,"extend":true}}""",
            json,
        )
    }

    @Test
    fun `목록은 items와 next_cursor로 쓴다`() {
        val json = jsonMapper.writeValueAsString(CursorPage(listOf("a"), null))

        assertEquals("""{"items":["a"],"next_cursor":null}""", json)
    }

    @Test
    fun `개인 소유자는 type이 user다`() {
        val json = jsonMapper.writeValueAsString(OwnerResponse.of(Owner.User("user-aaaaaaaaaaaa"), "홍길동"))

        assertEquals("""{"type":"user","id":"user-aaaaaaaaaaaa","name":"홍길동"}""", json)
    }
}
