package com.gsmsv.api.global.response

import com.gsmsv.api.global.exception.GlobalExceptionHandler
import jakarta.validation.Valid
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

class CursorPageRequestTest {
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(ListTestController())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    @Test
    fun `limit을 주지 않으면 20이다`() {
        mockMvc.get("/test/items").andExpect {
            status { isOk() }
            content { string("20:null") }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["1", "100"])
    fun `limit은 1부터 100까지 받는다`(limit: String) {
        mockMvc.get("/test/items") { param("limit", limit) }.andExpect {
            status { isOk() }
            content { string("$limit:null") }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "101", "-1", "abc"])
    fun `limit이 범위를 벗어나면 400 VALIDATION_FAILED를 돌려준다`(limit: String) {
        mockMvc.get("/test/items") { param("limit", limit) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.details.fields[0].field") { value("limit") }
            jsonPath("$.request_id") { isNotEmpty() }
        }
    }

    @Test
    fun `커서를 주면 디코딩해서 쓴다`() {
        val cursor = Cursor(Instant.parse("2026-11-04T01:02:03Z"), "vm-k3m9x2p7q4ta")

        mockMvc.get("/test/items") { param("cursor", cursor.encode()) }.andExpect {
            status { isOk() }
            content { string("20:vm-k3m9x2p7q4ta") }
        }
    }

    @Test
    fun `형식이 틀린 커서는 400 INVALID_CURSOR를 돌려준다`() {
        mockMvc.get("/test/items") { param("cursor", "!!!") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_CURSOR") }
        }
    }

    @RestController
    class ListTestController {
        @GetMapping("/test/items")
        fun list(
            @Valid @ModelAttribute request: CursorPageRequest,
        ): String = "${request.limit}:${request.decodedCursor()?.id}"
    }
}
