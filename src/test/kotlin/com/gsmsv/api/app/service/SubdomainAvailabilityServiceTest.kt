package com.gsmsv.api.app.service

import com.gsmsv.api.app.config.AppSubdomainProperties
import com.gsmsv.api.app.repository.EmptySubdomainAvailabilityRepository
import com.gsmsv.api.app.repository.SubdomainAvailabilityRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SubdomainAvailabilityServiceTest {
    private val repository = mockk<SubdomainAvailabilityRepository>()
    private val service = SubdomainAvailabilityService(AppSubdomainProperties(setOf("custom")), repository)

    @Test
    fun `유효하고 예약되지 않은 이름은 사용할 수 있다`() {
        every { repository.existsByName("my-app1") } returns false

        val result = service.check("my-app1")

        assertEquals(true, result.available)
        assertEquals(null, result.reason)
    }

    @Test
    fun `형식이 잘못된 이름은 조회 없이 거절한다`() {
        for (name in listOf("ab", "a".repeat(64), "1app", "App", "app-", "app.test", "app--test", "app_1")) {
            val result = service.check(name)
            assertEquals(false, result.available)
            assertEquals("invalid_format", result.reason)
        }
        verify(exactly = 0) { repository.existsByName(any()) }
    }

    @Test
    fun `기본 예약어와 추가 예약어는 조회 없이 거절한다`() {
        for (name in listOf(
            "www",
            "api",
            "admin",
            "gsmsv",
            "login",
            "auth",
            "official",
            "support",
            "help",
            "status",
            "docs",
            "mail",
            "custom",
        )) {
            val result = service.check(name)
            assertEquals(false, result.available)
            assertEquals("reserved", result.reason)
        }
        verify(exactly = 0) { repository.existsByName(any()) }
    }

    @Test
    fun `이미 사용 중인 이름은 taken으로 응답한다`() {
        every { repository.existsByName("existing") } returns true

        val result = service.check("existing")

        assertEquals(false, result.available)
        assertEquals("taken", result.reason)
    }

    @Test
    fun `저장 모델이 없는 기본 조회는 점유 항목이 없다`() {
        val defaultService = SubdomainAvailabilityService(AppSubdomainProperties(), EmptySubdomainAvailabilityRepository())

        assertEquals(null, defaultService.check("example").reason)
    }
}
