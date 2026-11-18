package com.gsmsv.api.vm.controller

import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import com.gsmsv.api.vm.service.VmImage
import com.gsmsv.api.vm.service.VmImageService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@ApiWebMvcTest(VmImageController::class)
class VmImageControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean
    private lateinit var vmImageService: VmImageService

    @Test
    fun `운영체제 목록을 명세의 칸 이름으로 돌려준다`() {
        every { vmImageService.list() } returns
            listOf(
                VmImage("ubuntu-24.04", "Ubuntu 24.04 LTS", minVcpu = 1, minRamGib = 1, minSsdGib = 4),
                VmImage("debian-13", "Debian 13", minVcpu = 1, minRamGib = 1, minSsdGib = 10),
            )

        mockMvc
            .get(URL) { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.items.length()") { value(2) }
                jsonPath("$.items[0].id") { value("ubuntu-24.04") }
                jsonPath("$.items[0].name") { value("Ubuntu 24.04 LTS") }
                jsonPath("$.items[0].min.vcpu") { value(1) }
                jsonPath("$.items[0].min.ram_gb") { value(1) }
                jsonPath("$.items[1].min.ssd_gb") { value(10) }
            }
    }

    @Test
    fun `토큰이 없으면 401을 돌려준다`() {
        mockMvc.get(URL).andExpect { status { isUnauthorized() } }
    }

    private companion object {
        const val URL = "/api/v2/vm-images"
        const val ME = "user-a1b2c3d4e5f6"
    }
}
