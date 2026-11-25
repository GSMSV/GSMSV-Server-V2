package com.gsmsv.api.vm.controller

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.dto.VmCredentialResponse
import com.gsmsv.api.vm.service.VmCredentialService
import com.gsmsv.api.vm.service.VmResourceReader
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant

@ApiWebMvcTest(VmCredentialController::class)
class VmCredentialControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean private lateinit var loader: ServiceResourceLoader

    @MockkBean private lateinit var vmReader: VmResourceReader

    @MockkBean private lateinit var credentialService: VmCredentialService

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
    fun `VIEW_ACCESS_INFO로 로더를 통과하고 no-store로 돌려준다`() {
        every { loader.load(UserActor(ME), GRN, Action.VIEW_ACCESS_INFO, vmReader) } returns vm
        every { credentialService.credentials(UserActor(ME), vm) } returns
            VmCredentialResponse(user = "ubuntu", initialPassword = "example-password-not-real", notice = "안내")

        mockMvc.get("/api/v2/vms/$GRN/credentials") { bearer(tokens.user(ME)) }.andExpect {
            status { isOk() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.user") { value("ubuntu") }
            jsonPath("$.initial_password") { value("example-password-not-real") }
            jsonPath("$.notice") { value("안내") }
        }
    }

    @Test
    fun `볼 수 없는 VM은 404이고 서비스를 부르지 않는다`() {
        every { loader.load(UserActor(ME), GRN, Action.VIEW_ACCESS_INFO, vmReader) } throws
            BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

        mockMvc.get("/api/v2/vms/$GRN/credentials") { bearer(tokens.user(ME)) }.andExpect { status { isNotFound() } }

        verify(exactly = 0) { credentialService.credentials(any(), any()) }
    }

    @Test
    fun `토큰이 없으면 401이다`() {
        mockMvc.get("/api/v2/vms/$GRN/credentials").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `GRN 형식이 틀리면 400 INVALID_GRN이다`() {
        mockMvc.get("/api/v2/vms/grn:$ME:vm-short/credentials") { bearer(tokens.user(ME)) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_GRN") }
        }
    }

    companion object {
        private const val ME = "user-a1b2c3d4e5f6"
        private const val GRN = "grn:$ME:vm-k3m9x2p7q4ta"
    }
}
