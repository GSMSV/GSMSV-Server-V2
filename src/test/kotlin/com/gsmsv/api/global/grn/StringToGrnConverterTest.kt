package com.gsmsv.api.global.grn

import com.gsmsv.api.global.exception.GlobalExceptionHandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.net.URI

class StringToGrnConverterTest {
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(TestController())
            .setControllerAdvice(GlobalExceptionHandler())
            .setConversionService(DefaultFormattingConversionService().apply { addConverter(StringToGrnConverter()) })
            .build()

    // URI로 넘긴다. 문자열 템플릿으로 넘기면 MockMvc가 `%`를 다시 인코딩해 `%253A`가 된다
    @ParameterizedTest
    @ValueSource(
        strings = [
            "grn:team-4p8w2k9m1xza:vm-k3m9x2p7q4ta",
            "grn%3Ateam-4p8w2k9m1xza%3Avm-k3m9x2p7q4ta",
            "grn%3ateam-4p8w2k9m1xza%3avm-k3m9x2p7q4ta",
            "grn:team-4p8w2k9m1xza%3Avm-k3m9x2p7q4ta",
        ],
    )
    fun `경로 변수의 콜론은 그대로든 퍼센트 인코딩이든 같은 GRN으로 받는다`(path: String) {
        mockMvc.get(URI.create("/test/vms/$path")).andExpect {
            status { isOk() }
            content { string("grn:team-4p8w2k9m1xza:vm-k3m9x2p7q4ta|vm-k3m9x2p7q4ta") }
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "grn:team-4p8w2k9m1xza:vm-k3m9x2p7",
            "grn:team-4p8w2k9m1xza:VM-k3m9x2p7q4ta",
            "vm-k3m9x2p7q4ta",
            // 디코딩한 값이 `%3A`로 남으면 형식 오류다 (두 번 인코딩한 요청)
            "grn%253Ateam-4p8w2k9m1xza%253Avm-k3m9x2p7q4ta",
        ],
    )
    fun `형식이 틀린 GRN은 400 INVALID_GRN으로 끝난다`(path: String) {
        mockMvc.get(URI.create("/test/vms/$path")).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_GRN") }
        }
    }

    @Test
    fun `형식은 맞지만 엔드포인트와 자원 종류가 다르면 400 GRN_RESOURCE_TYPE_MISMATCH로 끝난다`() {
        mockMvc.get(URI.create("/test/vms/grn:team-4p8w2k9m1xza:fn-6d2r8t5y3n7e")).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("GRN_RESOURCE_TYPE_MISMATCH") }
        }
    }

    @RestController
    class TestController {
        @GetMapping("/test/vms/{grn}")
        fun get(
            @PathVariable grn: Grn,
        ): String = "${grn.value}|${grn.requireType(ResourceType.VM).value}"
    }
}
