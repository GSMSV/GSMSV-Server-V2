package com.gsmsv.api.network.controller

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.network.dto.CreatePortForwardRequest
import com.gsmsv.api.network.dto.CreatePortForwardResponse
import com.gsmsv.api.network.dto.PortForwardLimitsResponse
import com.gsmsv.api.network.dto.PortForwardListResponse
import com.gsmsv.api.network.dto.PortForwardResponse
import com.gsmsv.api.network.service.PortForwardService
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

@ApiWebMvcTest(PortForwardController::class)
class PortForwardControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean
    private lateinit var loader: ServiceResourceLoader

    @MockkBean
    private lateinit var vmReader: VmResourceReader

    @MockkBean
    private lateinit var portForwardService: PortForwardService

    @Test
    fun `로더를 통과한 VM의 포트포워딩 목록을 snake_case로 돌려준다`() {
        val vm = vm()
        every { loader.load(UserActor(ME), GRN, Action.VIEW, vmReader) } returns vm
        every { portForwardService.list(vm) } returns
            PortForwardListResponse(
                items = listOf(PortForwardResponse("port-5q8w3n7z", "ssh", 20022, 22, "tcp", null, null)),
                publicHost = "vm.example.com",
                limits = PortForwardLimitsResponse(custom = 30),
            )

        mockMvc
            .get("/api/v2/vms/$GRN/port-forwards") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.items[0].id") { value("port-5q8w3n7z") }
                jsonPath("$.items[0].kind") { value("ssh") }
                jsonPath("$.items[0].external_port") { value(20022) }
                jsonPath("$.items[0].internal_port") { value(22) }
                jsonPath("$.items[0].protocol") { value("tcp") }
                jsonPath("$.items[0].allowed_source") { value(null) }
                jsonPath("$.public_host") { value("vm.example.com") }
                jsonPath("$.limits.custom") { value(30) }
            }
    }

    @Test
    fun `볼 수 없는 VM은 404다`() {
        every { loader.load(UserActor(ME), GRN, Action.VIEW, vmReader) } throws BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc
            .get("/api/v2/vms/$GRN/port-forwards") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
            }
    }

    @Test
    fun `GRN 형식이 틀리면 400 INVALID_GRN이다`() {
        mockMvc
            .get("/api/v2/vms/grn:$ME:vm-short/port-forwards") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_GRN") }
            }
    }

    @Test
    fun `토큰이 없으면 401이다`() {
        mockMvc.get("/api/v2/vms/$GRN/port-forwards").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `커스텀 포트를 추가하면 PORT_FORWARD 권한으로 VM을 불러 201을 돌려준다`() {
        val vm = vm()
        val request = CreatePortForwardRequest(8080, "tcp", "203.0.113.0/24", "개발용")
        every { loader.load(UserActor(ME), GRN, Action.PORT_FORWARD, vmReader) } returns vm
        every { portForwardService.create(vm, request) } returns
            CreatePortForwardResponse("port-2x7c5v9b", 40001, 8080, "tcp", "synced")

        mockMvc
            .post("/api/v2/vms/$GRN/port-forwards") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"internal_port":8080,"protocol":"tcp","allowed_source":"203.0.113.0/24","description":"개발용"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { value("port-2x7c5v9b") }
                jsonPath("$.external_port") { value(40001) }
                jsonPath("$.internal_port") { value(8080) }
                jsonPath("$.protocol") { value("tcp") }
                jsonPath("$.sync_status") { value("synced") }
            }
    }

    @Test
    fun `팀장·DevOps가 아니면 403이다`() {
        every { loader.load(UserActor(ME), GRN, Action.PORT_FORWARD, vmReader) } throws BusinessException(ErrorCode.ACCESS_DENIED)

        mockMvc
            .post("/api/v2/vms/$GRN/port-forwards") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"internal_port":8080,"protocol":"tcp"}"""
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("ACCESS_DENIED") }
            }
    }

    @Test
    fun `내부 포트가 범위를 벗어나거나 프로토콜·허용 출발지가 틀리면 400 VALIDATION_FAILED이고 VM을 불러오지 않는다`() {
        mockMvc
            .post("/api/v2/vms/$GRN/port-forwards") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"internal_port":70000,"protocol":"icmp","allowed_source":"example.com"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.details.fields.length()") { value(3) }
            }

        verify(exactly = 0) { loader.load(any(), any(), any(), vmReader) }
    }

    @Test
    fun `필수 칸이 빠지면 400 VALIDATION_FAILED다`() {
        mockMvc
            .post("/api/v2/vms/$GRN/port-forwards") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
    }

    @Test
    fun `외부 포트를 직접 보내면 400 UNKNOWN_FIELD다`() {
        mockMvc
            .post("/api/v2/vms/$GRN/port-forwards") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"internal_port":8080,"protocol":"tcp","external_port":40001}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("UNKNOWN_FIELD") }
            }
    }

    @Test
    fun `한도를 넘으면 422 QUOTA_EXCEEDED이고 빈 포트가 없으면 503 NO_FREE_PORT다`() {
        val vm = vm()
        every { loader.load(UserActor(ME), GRN, Action.PORT_FORWARD, vmReader) } returns vm
        every { portForwardService.create(vm, any()) } throws
            BusinessException(ErrorCode.QUOTA_EXCEEDED, details = mapOf("limit" to 30, "used" to 30)) andThenThrows
            BusinessException(ErrorCode.NO_FREE_PORT)

        val body = """{"internal_port":8080,"protocol":"udp"}"""
        mockMvc
            .post("/api/v2/vms/$GRN/port-forwards") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isUnprocessableContent() }
                jsonPath("$.code") { value("QUOTA_EXCEEDED") }
                jsonPath("$.details.limit") { value(30) }
            }
        mockMvc
            .post("/api/v2/vms/$GRN/port-forwards") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.code") { value("NO_FREE_PORT") }
            }
    }

    private fun vm(): Vm =
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

    companion object {
        private const val ME = "user-a1b2c3d4e5f6"
        private const val GRN = "grn:$ME:vm-k3m9x2p7q4ta"
    }
}
