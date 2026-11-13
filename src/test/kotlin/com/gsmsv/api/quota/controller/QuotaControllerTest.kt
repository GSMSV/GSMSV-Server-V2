package com.gsmsv.api.quota.controller

import com.gsmsv.api.quota.dto.CountUsageResponse
import com.gsmsv.api.quota.dto.MaxCreatableResponse
import com.gsmsv.api.quota.dto.MyAppQuotaResponse
import com.gsmsv.api.quota.dto.MyDbQuotaResponse
import com.gsmsv.api.quota.dto.MyFnQuotaResponse
import com.gsmsv.api.quota.dto.MyQuotaResponse
import com.gsmsv.api.quota.dto.MyVmQuotaResponse
import com.gsmsv.api.quota.dto.StorageUsageResponse
import com.gsmsv.api.quota.dto.VmQuotaUsageResponse
import com.gsmsv.api.quota.service.MyQuotaService
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.hamcrest.Matchers.hasKey
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@ApiWebMvcTest(QuotaController::class)
class QuotaControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean
    private lateinit var myQuotaService: MyQuotaService

    @Test
    fun `내 한도와 사용량을 명세의 칸 이름으로 돌려준다`() {
        every { myQuotaService.get(match { it.userId == ME }) } returns response(limit = 4, maxCreatable = 2)

        mockMvc
            .get(URL) { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.vm.vcpu.used") { value(2) }
                jsonPath("$.vm.vcpu.limit") { value(4) }
                jsonPath("$.vm.ram_gb.limit") { value(4) }
                jsonPath("$.vm.ssd_gb.used") { value(2) }
                jsonPath("$.vm.max_creatable.ram_gb") { value(2) }
                jsonPath("$.db.count.limit") { value(2) }
                jsonPath("$.db.shared_storage_gb.used") { value(0.12) }
                jsonPath("$.fn.daily_executions.limit") { value(10000) }
                jsonPath("$.app.memory_mb.limit") { value(2048) }
                jsonPath("$.app.always_on.limit") { value(1) }
            }
    }

    @Test
    fun `관리자는 limit과 max_creatable 칸이 null로 나간다`() {
        every { myQuotaService.get(match { it.userId == ME }) } returns response(limit = null, maxCreatable = null)

        mockMvc
            .get(URL) { bearer(tokens.admin(ME)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.vm.vcpu.limit") { value(null) }
                // 값이 null이어도 칸은 빠지지 않아야 한다 (명세의 required)
                jsonPath("$.vm.ram_gb") { value(hasKey("limit")) }
                jsonPath("$.vm.max_creatable") { value(hasKey("vcpu")) }
                jsonPath("$.vm.max_creatable.ssd_gb") { value(null) }
            }
    }

    @Test
    fun `토큰이 없으면 401을 돌려준다`() {
        mockMvc.get(URL).andExpect { status { isUnauthorized() } }
    }

    private fun response(
        limit: Int?,
        maxCreatable: Int?,
    ) = MyQuotaResponse(
        vm =
            MyVmQuotaResponse(
                vcpu = VmQuotaUsageResponse(2, limit),
                ramGb = VmQuotaUsageResponse(2, limit),
                ssdGb = VmQuotaUsageResponse(2, limit),
                maxCreatable = MaxCreatableResponse(maxCreatable, maxCreatable, maxCreatable),
            ),
        db = MyDbQuotaResponse(CountUsageResponse(1, 2), StorageUsageResponse(0.12, 1.0)),
        fn = MyFnQuotaResponse(CountUsageResponse(0, 3), CountUsageResponse(0, 10_000)),
        app = MyAppQuotaResponse(CountUsageResponse(1, 2), CountUsageResponse(0, 2048), CountUsageResponse(0, 1)),
    )

    private companion object {
        const val URL = "/api/v2/users/me/quota"
        const val ME = "user-a1b2c3d4e5f6"
    }
}
