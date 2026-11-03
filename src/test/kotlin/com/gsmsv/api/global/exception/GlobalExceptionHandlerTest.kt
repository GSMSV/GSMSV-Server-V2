package com.gsmsv.api.global.exception

import com.gsmsv.api.global.grn.GrnParser
import com.gsmsv.api.global.grn.ResourceType
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

class GlobalExceptionHandlerTest {
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(GrnTestController())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    @Test
    fun `형식이 틀린 GRN은 400과 공통 에러 형식을 돌려준다`() {
        mockMvc
            .get("/test/vms/grn:team-4p8w2k9m1xza:vm-k3m9x2p7") {
                header("X-Request-Id", "req-1")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_GRN") }
                jsonPath("$.message") { isNotEmpty() }
                jsonPath("$.request_id") { value("req-1") }
            }
    }

    @Test
    fun `엔드포인트와 자원 종류가 다른 GRN은 400을 돌려준다`() {
        mockMvc.get("/test/vms/grn:team-4p8w2k9m1xza:fn-6d2r8t5y3n7e").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("GRN_RESOURCE_TYPE_MISMATCH") }
            jsonPath("$.request_id") { isNotEmpty() }
        }
    }

    @RestController
    class GrnTestController {
        @GetMapping("/test/vms/{grn}")
        fun get(
            @PathVariable grn: String,
        ): String = GrnParser.parse(grn, ResourceType.VM).value
    }
}
