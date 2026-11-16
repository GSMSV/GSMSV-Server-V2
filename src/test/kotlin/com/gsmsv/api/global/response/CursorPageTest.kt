package com.gsmsv.api.global.response

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CursorPageTest {
    private data class Row(
        val id: String,
        val createdAt: Instant,
    )

    private val rows =
        (1..3).map { Row("vm-row$it", Instant.parse("2026-11-04T00:00:0${it}Z")) }

    @Test
    fun `limit보다 하나 더 읽혔으면 limit개만 담고 마지막 항목으로 다음 커서를 만든다`() {
        val page = CursorPage.of(rows, 2, { Cursor(it.createdAt, it.id) }, Row::id)

        assertEquals(listOf("vm-row1", "vm-row2"), page.items)
        assertEquals(Cursor(rows[1].createdAt, rows[1].id), Cursor.decode(page.nextCursor!!))
    }

    @Test
    fun `limit 이하로 읽혔으면 마지막 페이지라 다음 커서가 없다`() {
        val page = CursorPage.of(rows, 3, { Cursor(it.createdAt, it.id) }, Row::id)

        assertEquals(3, page.items.size)
        assertNull(page.nextCursor)
    }

    @Test
    fun `읽힌 항목이 없으면 빈 페이지다`() {
        val page = CursorPage.of(emptyList<Row>(), 20, { Cursor(it.createdAt, it.id) }, Row::id)

        assertEquals(emptyList(), page.items)
        assertNull(page.nextCursor)
    }
}
