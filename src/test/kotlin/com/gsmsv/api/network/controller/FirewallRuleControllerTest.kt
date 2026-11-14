package com.gsmsv.api.network.controller

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.LoadedSubResource
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.SubResourceLoader
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.network.domain.FirewallAction
import com.gsmsv.api.network.domain.FirewallDirection
import com.gsmsv.api.network.domain.FirewallProtocol
import com.gsmsv.api.network.domain.FirewallRule
import com.gsmsv.api.network.dto.CreateFirewallRuleRequest
import com.gsmsv.api.network.dto.CreateFirewallRuleResponse
import com.gsmsv.api.network.dto.FirewallRuleLimits
import com.gsmsv.api.network.dto.FirewallRulePageResponse
import com.gsmsv.api.network.dto.FirewallRuleResponse
import com.gsmsv.api.network.service.FirewallRuleResourceReader
import com.gsmsv.api.network.service.FirewallRuleService
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant

@ApiWebMvcTest(FirewallRuleController::class)
class FirewallRuleControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean
    private lateinit var loader: ServiceResourceLoader

    @MockkBean
    private lateinit var subResourceLoader: SubResourceLoader

    @MockkBean
    private lateinit var vmReader: VmResourceReader

    @MockkBean
    private lateinit var ruleReader: FirewallRuleResourceReader

    @MockkBean
    private lateinit var firewallRuleService: FirewallRuleService

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
    fun `VIEW로 VM을 불러와 목록을 snake_case로 돌려준다`() {
        every { loader.load(UserActor(ME), GRN, Action.VIEW, vmReader) } returns vm
        every { firewallRuleService.list(vm, any()) } returns
            FirewallRulePageResponse(
                enabled = true,
                items = listOf(FirewallRuleResponse(RULE_ID, "accept", "in", "tcp", "80", null)),
                limits = FirewallRuleLimits(50),
                nextCursor = null,
            )

        mockMvc
            .get("/api/v2/vms/$GRN/firewall-rules") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.enabled") { value(true) }
                jsonPath("$.items[0].id") { value(RULE_ID) }
                jsonPath("$.items[0].dport") { value("80") }
                jsonPath("$.items[0].source") { value(null) }
                jsonPath("$.limits.per_vm") { value(50) }
                jsonPath("$.next_cursor") { value(null) }
            }
    }

    @Test
    fun `limit이 범위를 벗어나면 400 VALIDATION_FAILED다`() {
        mockMvc
            .get("/api/v2/vms/$GRN/firewall-rules?limit=0") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
    }

    @Test
    fun `FIREWALL 권한으로 VM을 불러와 규칙을 추가하고 201로 id를 돌려준다`() {
        every { loader.load(UserActor(ME), GRN, Action.FIREWALL, vmReader) } returns vm
        val request = CreateFirewallRuleRequest(action = "drop", dport = "8000:8100", source = "203.0.113.0/24")
        every { firewallRuleService.create(vm, request) } returns CreateFirewallRuleResponse(RULE_ID)

        mockMvc
            .post("/api/v2/vms/$GRN/firewall-rules") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"action":"drop","dport":"8000:8100","source":"203.0.113.0/24"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { value(RULE_ID) }
            }
    }

    @Test
    fun `목적지 칸을 보내면 모르는 칸이라 400이다`() {
        mockMvc
            .post("/api/v2/vms/$GRN/firewall-rules") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = """{"dest":"198.51.100.1"}"""
            }.andExpect { status { isBadRequest() } }

        verify(exactly = 0) { firewallRuleService.create(any(), any()) }
    }

    @Test
    fun `권한이 없으면 403이다`() {
        every { loader.load(UserActor(ME), GRN, Action.FIREWALL, vmReader) } throws BusinessException(ErrorCode.ACCESS_DENIED)

        mockMvc
            .post("/api/v2/vms/$GRN/firewall-rules") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("ACCESS_DENIED") }
            }
    }

    @Test
    fun `가상화 서버가 응답하지 않으면 503이다`() {
        every { loader.load(UserActor(ME), GRN, Action.FIREWALL, vmReader) } returns vm
        every { firewallRuleService.create(vm, any()) } throws BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE)

        mockMvc
            .post("/api/v2/vms/$GRN/firewall-rules") {
                bearer(tokens.user(ME))
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.code") { value("UPSTREAM_UNAVAILABLE") }
            }
    }

    @Test
    fun `부모 VM GRN과 규칙 id로 불러와 지우고 204를 돌려준다`() {
        val rule = FirewallRule(RULE_ID, vm.id, FirewallAction.ACCEPT, FirewallDirection.IN, FirewallProtocol.TCP, "80", null)
        every {
            subResourceLoader.load(UserActor(ME), GRN, RULE_ID, Action.FIREWALL, vmReader, ruleReader)
        } returns LoadedSubResource(vm, rule)
        every { firewallRuleService.delete(vm, rule) } returns Unit

        mockMvc
            .delete("/api/v2/vms/$GRN/firewall-rules/$RULE_ID") { bearer(tokens.user(ME)) }
            .andExpect { status { isNoContent() } }

        verify { firewallRuleService.delete(vm, rule) }
    }

    @Test
    fun `다른 VM의 규칙 id면 404다`() {
        every {
            subResourceLoader.load(UserActor(ME), GRN, RULE_ID, Action.FIREWALL, vmReader, ruleReader)
        } throws BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc
            .delete("/api/v2/vms/$GRN/firewall-rules/$RULE_ID") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
            }
    }

    @Test
    fun `GRN 형식이 틀리면 400 INVALID_GRN이다`() {
        mockMvc
            .get("/api/v2/vms/grn:$ME:vm-short/firewall-rules") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_GRN") }
            }
    }

    @Test
    fun `토큰이 없으면 401이다`() {
        mockMvc.get("/api/v2/vms/$GRN/firewall-rules").andExpect { status { isUnauthorized() } }
    }

    companion object {
        private const val ME = "user-a1b2c3d4e5f6"
        private const val GRN = "grn:$ME:vm-k3m9x2p7q4ta"
        private const val RULE_ID = "fwrule-7m3k9p2x"
    }
}
