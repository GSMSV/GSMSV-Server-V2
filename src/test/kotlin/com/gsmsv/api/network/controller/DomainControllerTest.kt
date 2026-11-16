package com.gsmsv.api.network.controller

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.network.dto.CreateDomainResponse
import com.gsmsv.api.network.dto.DomainLimits
import com.gsmsv.api.network.dto.DomainListResponse
import com.gsmsv.api.network.dto.DomainResponse
import com.gsmsv.api.network.service.DomainService
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.service.VmResourceReader
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant

@ApiWebMvcTest(DomainController::class)
class DomainControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean
    private lateinit var loader: ServiceResourceLoader

    @MockkBean
    private lateinit var vmReader: VmResourceReader

    @MockkBean
    private lateinit var domainService: DomainService

    private val vm =
        Vm.create(
            id = "vm-k3m9x2p7q4ta",
            name = "테스트 VM",
            purpose = null,
            owner = Owner.User(ME),
            image = "ubuntu-24.04",
            vcpu = 2,
            ramGb = 4,
            ssdGb = 20,
            initialUsername = "ubuntu",
            initialPasswordEncrypted = "encrypted",
            createdBy = ME,
            expiresAt = Instant.parse("2027-01-01T00:00:00Z"),
        )

    @Test
    fun `로더를 통과한 VM의 도메인 목록을 snake_case로 돌려준다`() {
        every { loader.load(UserActor(ME), GRN, Action.VIEW, vmReader) } returns vm
        every { domainService.list(vm) } returns
            DomainListResponse(
                items = listOf(DomainResponse("domain-4k8m2q9t", "my-app", "https://my-app.example.com", 8080, "synced")),
                limits = DomainLimits(perVm = 2),
            )

        mockMvc
            .get("/api/v2/vms/$GRN/domains") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.items[0].id") { value("domain-4k8m2q9t") }
                jsonPath("$.items[0].subdomain") { value("my-app") }
                jsonPath("$.items[0].url") { value("https://my-app.example.com") }
                jsonPath("$.items[0].internal_port") { value(8080) }
                jsonPath("$.items[0].sync_status") { value("synced") }
                jsonPath("$.limits.per_vm") { value(2) }
            }
    }

    @Test
    fun `연결하면 DOMAIN 동작으로 로더를 부르고 201을 돌려준다`() {
        every { loader.load(UserActor(ME), GRN, Action.DOMAIN, vmReader) } returns vm
        every { domainService.create(vm, "my-app", 8080) } returns
            CreateDomainResponse("domain-4k8m2q9t", "https://my-app.example.com", "synced")

        mockMvc
            .post("/api/v2/vms/$GRN/domains") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"subdomain":"my-app","internal_port":8080}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { value("domain-4k8m2q9t") }
                jsonPath("$.url") { value("https://my-app.example.com") }
                jsonPath("$.sync_status") { value("synced") }
            }
    }

    @Test
    fun `서브도메인에 대문자가 있으면 400 VALIDATION_FAILED다`() {
        postInvalid("""{"subdomain":"My-App","internal_port":8080}""", "VALIDATION_FAILED")
    }

    @Test
    fun `서브도메인이 63자를 넘으면 400 VALIDATION_FAILED다`() {
        postInvalid("""{"subdomain":"${"a".repeat(64)}","internal_port":8080}""", "VALIDATION_FAILED")
    }

    @Test
    fun `내부 포트가 범위 밖이면 400 VALIDATION_FAILED다`() {
        postInvalid("""{"subdomain":"my-app","internal_port":65536}""", "VALIDATION_FAILED")
    }

    @Test
    fun `필수 칸이 빠지면 400 VALIDATION_FAILED다`() {
        postInvalid("""{"subdomain":"my-app"}""", "VALIDATION_FAILED")
    }

    @Test
    fun `명세에 없는 칸이 있으면 400이다`() {
        postInvalid("""{"subdomain":"my-app","internal_port":8080,"host":"x"}""", "UNKNOWN_FIELD")
    }

    @Test
    fun `팀 일반 팀원처럼 권한이 없으면 로더의 403을 그대로 돌려준다`() {
        every { loader.load(UserActor(ME), GRN, Action.DOMAIN, vmReader) } throws BusinessException(ErrorCode.ACCESS_DENIED)

        mockMvc
            .post("/api/v2/vms/$GRN/domains") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"subdomain":"my-app","internal_port":8080}"""
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("ACCESS_DENIED") }
            }
        verify(exactly = 0) { domainService.create(any(), any(), any()) }
    }

    @Test
    fun `볼 수 없는 VM은 404다`() {
        every { loader.load(UserActor(ME), GRN, Action.VIEW, vmReader) } throws BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc
            .get("/api/v2/vms/$GRN/domains") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
            }
    }

    @Test
    fun `GRN 형식이 틀리면 400 INVALID_GRN이다`() {
        mockMvc
            .get("/api/v2/vms/grn:$ME:vm-short/domains") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_GRN") }
            }
    }

    @Test
    fun `토큰이 없으면 401이다`() {
        mockMvc.get("/api/v2/vms/$GRN/domains").andExpect { status { isUnauthorized() } }
    }

    private fun postInvalid(
        body: String,
        code: String,
    ) {
        mockMvc
            .post("/api/v2/vms/$GRN/domains") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value(code) }
            }
        verify(exactly = 0) { domainService.create(any(), any(), any()) }
    }

    companion object {
        private const val ME = "user-a1b2c3d4e5f6"
        private const val GRN = "grn:$ME:vm-k3m9x2p7q4ta"
    }
}
