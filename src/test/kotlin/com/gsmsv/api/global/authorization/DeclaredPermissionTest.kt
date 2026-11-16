package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.exception.GlobalExceptionHandler
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/** `@RequirePermission`을 붙인 요청이 로더를 거쳐 규칙표대로 404·403이 나오는지 본다. */
class DeclaredPermissionTest {
    private val teamVm = FakeResource("vm-k3m9x2p7q4ta", Owner.Team(TEAM))
    private val grn = "grn:$TEAM:${teamVm.id}"

    private val loader =
        ServiceResourceLoader(
            AuthorizationPolicy(FakeTeamMembershipReader(mapOf(TEAM to mapOf(ME to TeamRole.LEADER, OTHER_USER to TeamRole.MEMBER)))),
            mockk(relaxed = true),
        )
    private val teamLoader = TeamLoader(AuthorizationPolicy(FakeTeamMembershipReader()), mockk(relaxed = true))

    @RestController
    inner class TestController {
        private val reader = FakeResourceReader(teamVm)

        @RequirePermission(Action.DELETE)
        @DeleteMapping("/test/vms/{grn}")
        fun delete(
            @PathVariable grn: String,
            @RequestHeader("X-User") userId: String,
        ): String = loader.load(UserActor(userId), grn, Action.DELETE, reader).id

        @RequirePermission(Action.VIEW)
        @GetMapping("/test/vms/{grn}")
        fun mismatch(
            @PathVariable grn: String,
        ): String = loader.load(UserActor(ME), grn, Action.DELETE, reader).id

        @Authenticated
        @PostMapping("/test/vms/{grn}")
        fun undeclared(
            @PathVariable grn: String,
        ): String = loader.load(UserActor(ME), grn, Action.VIEW, reader).id

        @Authenticated
        @PostMapping("/test/teams/{teamId}")
        fun undeclaredTeam(
            @PathVariable teamId: String,
        ): String = teamLoader.load(UserActor(ME), teamId, Action.VIEW, FakeTeamReader()).id
    }

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(TestController())
            .setControllerAdvice(GlobalExceptionHandler())
            .addInterceptors(DeclaredPermissionInterceptor())
            .build()

    @Test
    fun `선언한 동작의 권한이 있으면 통과한다`() {
        mockMvc.delete("/test/vms/$grn") { header("X-User", ME) }.andExpect {
            status { isOk() }
            content { string(teamVm.id) }
        }
    }

    @Test
    fun `볼 수 있지만 권한이 없으면 403이다`() {
        mockMvc.delete("/test/vms/$grn") { header("X-User", OTHER_USER) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ACCESS_DENIED") }
        }
    }

    @Test
    fun `볼 수 없으면 404다`() {
        mockMvc.delete("/test/vms/$grn") { header("X-User", "user-c3d4e5f6g7h8") }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
        }
    }

    @Test
    fun `선언과 다른 동작으로 로더를 부르면 자원을 돌려주지 않고 실패한다`() {
        mockMvc.get("/test/vms/$grn").andExpect { status { isInternalServerError() } }
    }

    @Test
    fun `@RequirePermission이 없는 엔드포인트에서 로더를 부르면 실패한다`() {
        mockMvc.post("/test/vms/$grn").andExpect { status { isInternalServerError() } }
        mockMvc.post("/test/teams/$TEAM").andExpect { status { isInternalServerError() } }
    }

    @Test
    fun `요청 밖에서는 선언 없이 로더를 부를 수 있다`() {
        loader.load(SystemActor, grn, Action.DELETE, FakeResourceReader(teamVm))
    }
}
