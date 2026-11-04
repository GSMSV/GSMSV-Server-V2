package com.gsmsv.api.global.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.databind.exc.UnrecognizedPropertyException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@JsonTest
class JacksonSettingsTest(
    @Autowired private val jsonMapper: JsonMapper,
) {
    data class Sample(
        val vmName: String,
        val createdAt: Instant,
    )

    data class OffsetSample(
        val expiresAt: OffsetDateTime,
    )

    @Test
    fun `필드 이름을 snake_case로 쓴다`() {
        val json = jsonMapper.writeValueAsString(Sample("web", Instant.parse("2026-11-04T01:02:03Z")))

        assertEquals("""{"vm_name":"web","created_at":"2026-11-04T01:02:03Z"}""", json)
    }

    @Test
    fun `오프셋이 있는 시간도 UTC로 바꿔 쓴다`() {
        val time = OffsetDateTime.of(2026, 11, 4, 10, 2, 3, 0, ZoneOffset.ofHours(9))

        val json = jsonMapper.writeValueAsString(OffsetSample(time))

        assertEquals("""{"expires_at":"2026-11-04T01:02:03Z"}""", json)
    }

    @Test
    fun `snake_case 요청을 읽는다`() {
        val sample = jsonMapper.readValue<Sample>("""{"vm_name":"web","created_at":"2026-11-04T10:02:03+09:00"}""")

        assertEquals(Sample("web", Instant.parse("2026-11-04T01:02:03Z")), sample)
    }

    @Test
    fun `모르는 필드가 있으면 거부한다`() {
        assertFailsWith<UnrecognizedPropertyException> {
            jsonMapper.readValue<Sample>("""{"vm_name":"web","created_at":"2026-11-04T01:02:03Z","extra":1}""")
        }
    }
}
