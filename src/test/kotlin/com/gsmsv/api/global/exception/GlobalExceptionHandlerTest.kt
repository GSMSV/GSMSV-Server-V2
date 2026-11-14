package com.gsmsv.api.global.exception

import com.gsmsv.api.global.config.RequestIdFilter
import com.gsmsv.api.global.grn.GrnParser
import com.gsmsv.api.global.grn.ResourceType
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.module.kotlin.jacksonMapperBuilder
import kotlin.test.assertEquals

class GlobalExceptionHandlerTest {
    // application.yml의 Jackson 설정(snake_case, 모르는 필드 거부)과 같게 맞춘다
    private val jsonMapper =
        jacksonMapperBuilder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(TestController())
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(JacksonJsonHttpMessageConverter(jsonMapper))
            .addFilters<StandaloneMockMvcBuilder>(RequestIdFilter())
            .build()

    @Test
    fun `형식이 틀린 GRN은 400과 공통 오류 형식을 돌려준다`() {
        mockMvc
            .get("/test/vms/grn:team-4p8w2k9m1xza:vm-k3m9x2p7") {
                header("X-Request-Id", "req-1")
            }.andExpect {
                status { isBadRequest() }
                header { string("X-Request-Id", "req-1") }
                jsonPath("$.code") { value("INVALID_GRN") }
                jsonPath("$.message") { isNotEmpty() }
                jsonPath("$.request_id") { value("req-1") }
                jsonPath("$.details") { doesNotExist() }
            }
    }

    @Test
    fun `엔드포인트와 자원 종류가 다른 GRN은 400을 돌려준다`() {
        mockMvc.get("/test/vms/grn:team-4p8w2k9m1xza:fn-6d2r8t5y3n7e").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("GRN_RESOURCE_TYPE_MISMATCH") }
        }
    }

    @Test
    fun `BusinessException의 details를 응답에 담는다`() {
        mockMvc.get("/test/details").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ACCESS_DENIED") }
            jsonPath("$.details.required_role") { value("LEADER") }
            jsonPath("$.message") { value("팀장만 할 수 있습니다.") }
        }
    }

    @Test
    fun `BusinessException의 응답 헤더를 오류 응답에 보존한다`() {
        val result =
            mockMvc
                .get("/test/headers")
                .andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.code") { value("UNAUTHENTICATED") }
                }.andReturn()

        assert(result.response.getHeaders(HttpHeaders.SET_COOKIE).any { it.startsWith("refresh_token=; Max-Age=0") })
    }

    @Test
    fun `검증 실패는 400 VALIDATION_FAILED와 snake_case 필드 목록을 돌려준다`() {
        post("""{"vm_name":"","cpu_count":0}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.details.fields.length()") { value(2) }
            jsonPath("$.details.fields[?(@.field == 'vm_name')].reason") { isNotEmpty() }
            jsonPath("$.details.fields[?(@.field == 'cpu_count')].reason") { isNotEmpty() }
        }
    }

    @Test
    fun `타입 변환 실패의 reason은 내부 타입과 입력 값 없이 고정 문구를 돌려준다`() {
        mockMvc.get("/test/page?limit=abc").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.details.fields[0].field") { value("limit") }
            jsonPath("$.details.fields[0].reason") { value("형식이 올바르지 않습니다.") }
        }
    }

    @Test
    fun `모르는 필드가 있으면 400 UNKNOWN_FIELD와 필드 이름을 돌려준다`() {
        post("""{"vm_name":"web","cpu_count":1,"extra_field":1}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("UNKNOWN_FIELD") }
            jsonPath("$.details.field") { value("extra_field") }
        }
    }

    @Test
    fun `읽을 수 없는 본문은 400 INVALID_REQUEST를 돌려준다`() {
        post("""{"vm_name":""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `처리하지 못한 예외는 500 INTERNAL을 돌려주고 내부 내용을 숨긴다`() {
        val body =
            mockMvc
                .get("/test/boom")
                .andExpect {
                    status { isInternalServerError() }
                    jsonPath("$.code") { value("INTERNAL") }
                    jsonPath("$.details") { doesNotExist() }
                }.andReturn()
                .response.contentAsString

        assert(!body.contains("secret-detail")) { body }
        assert(!body.contains("IllegalStateException")) { body }
    }

    @Test
    fun `없는 API는 404 ENDPOINT_NOT_FOUND를 돌려준다`() {
        mockMvc.get("/test/missing").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ENDPOINT_NOT_FOUND") }
        }
    }

    @Test
    fun `지원하지 않는 메서드는 405 METHOD_NOT_ALLOWED를 돌려준다`() {
        mockMvc.post("/test/boom").andExpect {
            status { isMethodNotAllowed() }
            header { exists("Allow") }
            jsonPath("$.code") { value("METHOD_NOT_ALLOWED") }
        }
    }

    @Test
    fun `요청 id가 없으면 만들어서 응답 헤더와 본문에 같은 값을 넣는다`() {
        val response =
            mockMvc
                .get("/test/boom")
                .andReturn()
                .response
        val header = response.getHeader("X-Request-Id")
        val body = jsonMapper.readTree(response.contentAsString)

        assert(header!!.startsWith("req-")) { header }
        assertEquals(header, body["request_id"].asString())
    }

    private fun post(json: String): ResultActionsDsl =
        mockMvc.post("/test/vms") {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }

    data class CreateVmRequest(
        @field:NotBlank
        val vmName: String,
        @field:Min(1)
        val cpuCount: Int,
    )

    data class PageQuery(
        @field:Min(1)
        val limit: Int = 20,
    )

    @RestController
    class TestController {
        @GetMapping("/test/page")
        fun page(
            @Valid query: PageQuery,
        ): Int = query.limit

        @GetMapping("/test/vms/{grn}")
        fun get(
            @PathVariable grn: String,
        ): String = GrnParser.parse(grn, ResourceType.VM).value

        @PostMapping("/test/vms")
        fun create(
            @Valid @RequestBody request: CreateVmRequest,
        ): String = request.vmName

        @GetMapping("/test/details")
        fun details(): String = throw BusinessException(ErrorCode.ACCESS_DENIED, mapOf("required_role" to "LEADER"), "팀장만 할 수 있습니다.")

        @GetMapping("/test/headers")
        fun headers(): String =
            throw BusinessException(
                ErrorCode.UNAUTHENTICATED,
                responseHeaders = HttpHeaders().apply { add(HttpHeaders.SET_COOKIE, "refresh_token=; Max-Age=0") },
            )

        @GetMapping("/test/boom")
        fun boom(): String = throw IllegalStateException("secret-detail")
    }
}
