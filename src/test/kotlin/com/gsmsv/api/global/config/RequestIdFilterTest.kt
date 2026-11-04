package com.gsmsv.api.global.config

import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestIdFilterTest {
    private val filter = RequestIdFilter()

    private fun run(header: String?): Pair<MockHttpServletRequest, MockHttpServletResponse> {
        val request = MockHttpServletRequest().apply { header?.let { addHeader("X-Request-Id", it) } }
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, MockFilterChain())
        return request to response
    }

    @Test
    fun `형식이 맞는 요청 id는 그대로 쓴다`() {
        val (request, response) = run("req-abc_123")

        assertEquals("req-abc_123", response.getHeader("X-Request-Id"))
        assertEquals("req-abc_123", request.getAttribute(RequestIdFilter.ATTRIBUTE))
    }

    @Test
    fun `요청 id가 없으면 req- 접두사로 만든다`() {
        val (request, response) = run(null)

        val requestId = response.getHeader("X-Request-Id")!!
        assertTrue(requestId.startsWith("req-"), requestId)
        assertEquals(requestId, request.getAttribute(RequestIdFilter.ATTRIBUTE))
    }

    @Test
    fun `형식이 틀린 요청 id는 버리고 새로 만든다`() {
        listOf("abc", "req-", "req-a b", "req-a\nb", "req-" + "a".repeat(65)).forEach { invalid ->
            val requestId = run(invalid).second.getHeader("X-Request-Id")!!

            assertNotEquals(invalid, requestId)
            assertTrue(requestId.startsWith("req-"), requestId)
        }
    }

    @Test
    fun `요청이 끝나면 MDC에서 요청 id를 지운다`() {
        run("req-1")

        assertNull(MDC.get(RequestIdFilter.MDC_KEY))
    }
}
