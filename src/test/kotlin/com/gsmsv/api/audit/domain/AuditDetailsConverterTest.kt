package com.gsmsv.api.audit.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuditDetailsConverterTest {
    private val converter = AuditDetailsConverter()

    @Test
    fun `부가 정보를 JSON으로 저장하고 그대로 읽는다`() {
        val details = mapOf("original_owner" to "team-4p8w2k9m1xza", "memo" to "탈퇴한 소유자 대신 지정")

        val column = converter.convertToDatabaseColumn(details)

        assertEquals(details, converter.convertToEntityAttribute(column))
    }

    @Test
    fun `부가 정보가 없으면 칸을 비우고 빈 맵으로 읽는다`() {
        assertNull(converter.convertToDatabaseColumn(emptyMap()))
        assertEquals(emptyMap(), converter.convertToEntityAttribute(null))
    }
}
