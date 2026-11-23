package com.gsmsv.api.app.controller

import com.gsmsv.api.app.dto.SubdomainAvailabilityResponse
import com.gsmsv.api.app.service.SubdomainAvailabilityService
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@ApiWebMvcTest(SubdomainAvailabilityController::class)
class SubdomainAvailabilityControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean private lateinit var service: SubdomainAvailabilityService

    @Test
    fun `계약 응답에는 available과 reason만 있다`() {
        every { service.check("example") } returns SubdomainAvailabilityResponse(true, null)

        mockMvc
            .get(PATH) {
                param("name", "example")
                bearer(tokens.user("user-a1b2c3d4e5f6"))
            }.andExpect {
                status { isOk() }
                jsonPath("$.available") { value(true) }
                jsonPath("$.reason") { value(null) }
                jsonPath("$.name") { doesNotExist() }
            }
    }

    @Test
    fun `형식 오류도 200과 invalid_format으로 응답한다`() {
        every { service.check("Bad") } returns SubdomainAvailabilityResponse(false, "invalid_format")

        mockMvc
            .get(PATH) {
                param("name", "Bad")
                bearer(tokens.user("user-a1b2c3d4e5f6"))
            }.andExpect {
                status { isOk() }
                jsonPath("$.available") { value(false) }
                jsonPath("$.reason") { value("invalid_format") }
            }
    }

    @Test
    fun `인증되지 않은 요청은 401이다`() {
        mockMvc.get(PATH) { param("name", "example") }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `name 쿼리가 없으면 400이다`() {
        mockMvc.get(PATH) { bearer(tokens.user("user-a1b2c3d4e5f6")) }.andExpect { status { isBadRequest() } }
    }

    companion object {
        private const val PATH = "/api/v2/apps/subdomains/availability"
    }
}
