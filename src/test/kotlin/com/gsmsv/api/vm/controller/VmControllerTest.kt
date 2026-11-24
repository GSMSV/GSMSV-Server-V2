package com.gsmsv.api.vm.controller

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.global.response.OwnerResponse
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.dto.VmMetricPointResponse
import com.gsmsv.api.vm.dto.VmMetricsResponse
import com.gsmsv.api.vm.dto.VmSpecResponse
import com.gsmsv.api.vm.dto.VmSummaryResponse
import com.gsmsv.api.vm.service.VmMetricsService
import com.gsmsv.api.vm.service.VmQueryService
import com.gsmsv.api.vm.service.VmResourceReader
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant

@ApiWebMvcTest(VmController::class)
class VmControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean private lateinit var loader: ServiceResourceLoader

    @MockkBean private lateinit var vmReader: VmResourceReader

    @MockkBean private lateinit var queries: VmQueryService

    @MockkBean private lateinit var metrics: VmMetricsService

    @Test
    fun `목록은 owner를 해석해 넘기고 null 칸도 snake_case로 내보낸다`() {
        every { queries.list(UserActor(ME), Owner.User(ME), any()) } returns CursorPage(listOf(summary()), null)

        mockMvc.get("/api/v2/vms?owner=me") { bearer(tokens.user(ME)) }.andExpect {
            status { isOk() }
            jsonPath("$.items[0].power_state") { value("unknown") }
            jsonPath("$.items[0].usage") { value(null) }
            jsonPath("$.items[0].internal_ip") { value(null) }
            jsonPath("$.items[0].spec.ram_gb") { value(4) }
            jsonPath("$.next_cursor") { value(null) }
        }
        mockMvc.get("/api/v2/vms").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `owner 없이는 전부, 팀 id면 그 팀으로 좁힌다`() {
        every { queries.list(UserActor(ME), any(), any()) } returns CursorPage(emptyList(), null)

        mockMvc.get("/api/v2/vms") { bearer(tokens.user(ME)) }.andExpect { status { isOk() } }
        mockMvc.get("/api/v2/vms?owner=$TEAM_ID") { bearer(tokens.user(ME)) }.andExpect { status { isOk() } }

        verify { queries.list(UserActor(ME), null, any()) }
        verify { queries.list(UserActor(ME), Owner.Team(TEAM_ID), any()) }
    }

    @Test
    fun `owner 형식이 틀리면 400이다`() {
        mockMvc.get("/api/v2/vms?owner=team-bad") { bearer(tokens.user(ME)) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_RESOURCE_ID") }
        }
    }

    @Test
    fun `잘못된 GRN은 400이고 보이지 않는 VM은 404다`() {
        mockMvc.get("/api/v2/vms/grn:$ME:vm-short") { bearer(tokens.user(ME)) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_GRN") }
        }
        every { loader.load(UserActor(ME), GRN, Action.VIEW, vmReader) } throws BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        mockMvc.get("/api/v2/vms/$GRN") { bearer(tokens.user(ME)) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
        }
    }

    @Test
    fun `상세는 로더를 통과한 VM으로만 만든다`() {
        val vm =
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
        every { loader.load(UserActor(ME), GRN, Action.VIEW, vmReader) } returns vm
        every { queries.get(UserActor(ME), vm) } throws BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc.get("/api/v2/vms/$GRN") { bearer(tokens.user(ME)) }.andExpect { status { isNotFound() } }

        verify(exactly = 1) { queries.get(UserActor(ME), vm) }
    }

    @Test
    fun `사용량 그래프는 로더를 통과한 VM과 period로 만들고 null 칸도 snake_case로 내보낸다`() {
        val vm = vm()
        every { loader.load(UserActor(ME), GRN, Action.VIEW, vmReader) } returns vm
        every { metrics.get(vm, "day") } returns
            VmMetricsResponse(
                period = "day",
                points =
                    listOf(
                        VmMetricPointResponse(Instant.parse("2026-11-24T09:00:00Z"), 3.1, 1650.0, 12000.0, 8000.0, 0.0, 4096.0),
                        VmMetricPointResponse(Instant.parse("2026-11-24T09:30:00Z"), null, null, null, null, null, null),
                    ),
            )

        mockMvc.get("/api/v2/vms/$GRN/metrics?period=day") { bearer(tokens.user(ME)) }.andExpect {
            status { isOk() }
            jsonPath("$.period") { value("day") }
            jsonPath("$.points[0].at") { value("2026-11-24T09:00:00Z") }
            jsonPath("$.points[0].cpu_percent") { value(3.1) }
            jsonPath("$.points[0].memory_mb") { value(1650.0) }
            jsonPath("$.points[0].net_in_bps") { value(12000.0) }
            jsonPath("$.points[0].net_out_bps") { value(8000.0) }
            jsonPath("$.points[0].disk_read_bps") { value(0.0) }
            jsonPath("$.points[0].disk_write_bps") { value(4096.0) }
            jsonPath("$.points[1].cpu_percent") { value(null) }
        }
        mockMvc.get("/api/v2/vms/$GRN/metrics").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `사용량 그래프는 period가 없으면 null로 넘기고 서비스 오류를 그대로 돌려준다`() {
        val vm = vm()
        every { loader.load(UserActor(ME), GRN, Action.VIEW, vmReader) } returns vm
        every { metrics.get(vm, null) } throws BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE)
        every { metrics.get(vm, "week") } throws BusinessException(ErrorCode.VALIDATION_FAILED)

        mockMvc.get("/api/v2/vms/$GRN/metrics") { bearer(tokens.user(ME)) }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("UPSTREAM_UNAVAILABLE") }
        }
        mockMvc.get("/api/v2/vms/$GRN/metrics?period=week") { bearer(tokens.user(ME)) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `사용량 그래프는 보이지 않는 VM이면 지표를 묻지 않고 404다`() {
        every { loader.load(UserActor(ME), GRN, Action.VIEW, vmReader) } throws BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc.get("/api/v2/vms/$GRN/metrics?period=week") { bearer(tokens.user(ME)) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
        }
        verify(exactly = 0) { metrics.get(any(), any()) }
    }

    private fun vm() =
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

    private fun summary() =
        VmSummaryResponse(
            grn = GRN,
            id = "vm-k3m9x2p7q4ta",
            name = "테스트 VM",
            owner = OwnerResponse.of(Owner.User(ME), "홍길동"),
            status = "creating",
            powerState = "unknown",
            image = "ubuntu-24.04",
            spec = VmSpecResponse(vcpu = 2, ramGb = 4, ssdGb = 20),
            usage = null,
            internalIp = null,
            purpose = null,
            createdAt = Instant.parse("2026-11-01T00:00:00Z"),
            expiresAt = Instant.parse("2027-01-01T00:00:00Z"),
            permissions = mapOf("delete" to true, "power" to true, "extend" to true),
        )

    companion object {
        private const val ME = "user-a1b2c3d4e5f6"
        private const val TEAM_ID = "team-4p8w2k9m1xza"
        private const val GRN = "grn:$ME:vm-k3m9x2p7q4ta"
    }
}
