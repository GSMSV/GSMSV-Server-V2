package com.gsmsv.api.monitoring.controller

import com.gsmsv.api.global.authorization.AdminActor
import com.gsmsv.api.global.authorization.AuthorizationAuditLogger
import com.gsmsv.api.global.authorization.AuthorizationPolicy
import com.gsmsv.api.global.authorization.FakeTeamMembershipReader
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.PlatformLoader
import com.gsmsv.api.global.grn.ResourceType
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.monitoring.dto.AdminResourceResponse
import com.gsmsv.api.monitoring.service.AdminResourceFilter
import com.gsmsv.api.monitoring.service.AdminResourceService
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals

@ApiWebMvcTest(AdminResourceController::class)
@Import(AdminResourceControllerTest.PlatformLoaderConfig::class)
class AdminResourceControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @MockkBean
    private lateinit var adminResourceService: AdminResourceService

    @Test
    fun `관리자에게 snake_case 칸으로 목록을 돌려준다`() {
        every { adminResourceService.list(AdminActor(ADMIN), AdminResourceFilter(), any()) } returns
            CursorPage(
                listOf(
                    AdminResourceResponse(
                        grn = "grn:$OWNER:vm-k3m9x2p7q4ta",
                        type = "vm",
                        name = "웹 서버",
                        node = null,
                        vmid = null,
                        ownerEmail = "owner@gsm.hs.kr",
                        status = "active",
                        powerState = null,
                    ),
                ),
                null,
            )

        mockMvc
            .get("/api/v2/admin/resources") { bearer(tokens.admin(ADMIN)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.items[0].grn") { value("grn:$OWNER:vm-k3m9x2p7q4ta") }
                jsonPath("$.items[0].owner_email") { value("owner@gsm.hs.kr") }
                jsonPath("$.items[0].power_state") { value(null) }
                jsonPath("$.items[0].vmid") { value(null) }
                jsonPath("$.next_cursor") { value(null) }
            }
    }

    @Test
    fun `type·node·owner를 거르기 조건으로 넘긴다`() {
        val filter = slot<AdminResourceFilter>()
        every { adminResourceService.list(AdminActor(ADMIN), capture(filter), any()) } returns CursorPage(emptyList(), null)

        mockMvc
            .get("/api/v2/admin/resources?type=db&node=node1&owner=$TEAM") { bearer(tokens.admin(ADMIN)) }
            .andExpect { status { isOk() } }

        assertEquals(AdminResourceFilter(ResourceType.DATABASE, "node1", Owner.Team(TEAM)), filter.captured)
    }

    @Test
    fun `owner가 사용자 id면 개인 소유자로 거른다`() {
        val filter = slot<AdminResourceFilter>()
        every { adminResourceService.list(AdminActor(ADMIN), capture(filter), any()) } returns CursorPage(emptyList(), null)

        mockMvc
            .get("/api/v2/admin/resources?owner=$OWNER") { bearer(tokens.admin(ADMIN)) }
            .andExpect { status { isOk() } }

        assertEquals(Owner.User(OWNER), filter.captured.owner)
    }

    @Test
    fun `관리자가 아니면 403이고 목록을 꺼내지 않는다`() {
        mockMvc
            .get("/api/v2/admin/resources") { bearer(tokens.user(OWNER)) }
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("ACCESS_DENIED") }
                jsonPath("$.details.required_role") { value("ADMIN_ONLY") }
            }

        verify(exactly = 0) { adminResourceService.list(any(), any(), any()) }
    }

    @Test
    fun `type이 목록에 없으면 400이다`() {
        mockMvc
            .get("/api/v2/admin/resources?type=snap") { bearer(tokens.admin(ADMIN)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
    }

    @Test
    fun `owner 형식이 틀리면 400이다`() {
        mockMvc
            .get("/api/v2/admin/resources?owner=nobody") { bearer(tokens.admin(ADMIN)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_RESOURCE_ID") }
            }
    }

    @Test
    fun `limit이 범위를 벗어나면 400이다`() {
        mockMvc
            .get("/api/v2/admin/resources?limit=101") { bearer(tokens.admin(ADMIN)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
    }

    @Test
    fun `토큰이 없으면 401이다`() {
        mockMvc.get("/api/v2/admin/resources").andExpect { status { isUnauthorized() } }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class PlatformLoaderConfig {
        @Bean
        fun platformLoader() = PlatformLoader(AuthorizationPolicy(FakeTeamMembershipReader()), AuthorizationAuditLogger())
    }

    private companion object {
        const val ADMIN = "user-a1b2c3d4e5f6"
        const val OWNER = "user-b2c3d4e5f6g7"
        const val TEAM = "team-c3d4e5f6g7h8"
    }
}
