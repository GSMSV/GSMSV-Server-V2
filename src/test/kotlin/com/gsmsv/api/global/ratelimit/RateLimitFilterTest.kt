package com.gsmsv.api.global.ratelimit

import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.Role
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RateLimitFilterTest {
    private fun filter(
        vararg rules: RateLimitProperties.Rule,
        enabled: Boolean = true,
    ) = RateLimitFilter(RateLimitProperties(enabled, rules.toList()), RateLimiter(), JsonMapper.builder().build())

    private val ipRule =
        RateLimitProperties.Rule("login", listOf("/api/v2/auth/**"), RateLimitProperties.Key.IP, 2)
    private val userRule =
        RateLimitProperties.Rule("avail", listOf("/api/v2/apps/subdomains/availability"), RateLimitProperties.Key.USER, 1)

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    private fun call(
        filter: RateLimitFilter,
        path: String,
        ip: String = "10.0.0.1",
    ): MockHttpServletResponse {
        val request = MockHttpServletRequest("GET", path).apply { remoteAddr = ip }
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, MockFilterChain())
        return response
    }

    @Test
    fun `한도를 넘으면 429와 Retry-After와 RATE_LIMITED를 돌려준다`() {
        val filter = filter(ipRule)
        repeat(2) { assertEquals(200, call(filter, "/api/v2/auth/token-exchange").status) }

        val response = call(filter, "/api/v2/auth/token-exchange")

        assertEquals(429, response.status)
        assertEquals("60", response.getHeader("Retry-After"))
        assertTrue(response.contentAsString.contains("\"code\":\"RATE_LIMITED\""))
        assertNotNull(response.getHeader("Retry-After"))
    }

    @Test
    fun `IP마다 따로 센다`() {
        val filter = filter(ipRule)
        repeat(2) { call(filter, "/api/v2/auth/x", ip = "10.0.0.1") }

        assertEquals(200, call(filter, "/api/v2/auth/x", ip = "10.0.0.2").status)
    }

    @Test
    fun `규칙에 없는 경로는 제한하지 않는다`() {
        val filter = filter(ipRule)

        repeat(10) { assertEquals(200, call(filter, "/api/v2/teams").status) }
    }

    @Test
    fun `사용자 키는 사용자마다 따로 세고 인증되지 않았으면 적용하지 않는다`() {
        val filter = filter(userRule)
        val path = "/api/v2/apps/subdomains/availability"

        repeat(5) { assertEquals(200, call(filter, path).status) }

        login("user-aaaaaaaaaaaa")
        assertEquals(200, call(filter, path).status)
        assertEquals(429, call(filter, path).status)

        login("user-bbbbbbbbbbbb")
        assertEquals(200, call(filter, path).status)
    }

    @Test
    fun `꺼 두면 제한하지 않는다`() {
        val filter = filter(ipRule, enabled = false)

        repeat(10) { assertEquals(200, call(filter, "/api/v2/auth/x").status) }
        assertNull(call(filter, "/api/v2/auth/x").getHeader("Retry-After"))
    }

    private fun login(userId: String) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(AuthenticatedUser(userId, Role.USER), null, emptyList())
    }
}
