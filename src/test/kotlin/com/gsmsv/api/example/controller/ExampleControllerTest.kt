package com.gsmsv.api.example.controller

import com.gsmsv.api.example.ExampleTestConfig
import com.gsmsv.api.example.ExampleTestConfig.Companion.LOCKED_EXAMPLE
import com.gsmsv.api.example.ExampleTestConfig.Companion.ME
import com.gsmsv.api.example.ExampleTestConfig.Companion.MY_EXAMPLE
import com.gsmsv.api.example.ExampleTestConfig.Companion.OTHER_USER_EXAMPLE
import com.gsmsv.api.example.ExampleTestConfig.Companion.TEAM_EXAMPLE
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get

/** 골격 완료 기준 테스트 (#25). 새 도메인 컨트롤러 테스트도 이 모양으로 쓴다 */
@ApiWebMvcTest(ExampleController::class)
@Import(ExampleTestConfig::class)
class ExampleControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
) {
    @Test
    fun `토큰이 있으면 내 자원을 200으로 돌려준다`() {
        mockMvc
            .get("/api/v2/examples/$MY_EXAMPLE") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.grn") { value(MY_EXAMPLE) }
                jsonPath("$.owner.type") { value("user") }
                jsonPath("$.created_at") { value("2026-01-01T00:00:00Z") }
                jsonPath("$.permissions.delete") { value(true) }
            }
    }

    @Test
    fun `토큰이 없으면 401 UNAUTHENTICATED를 돌려준다`() {
        mockMvc.get("/api/v2/examples/$MY_EXAMPLE").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHENTICATED") }
        }
    }

    @Test
    fun `GRN 형식이 틀리면 400 INVALID_GRN을 돌려준다`() {
        mockMvc
            .get("/api/v2/examples/grn:$ME:vm-short") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_GRN") }
            }
    }

    @Test
    fun `남의 개인 자원은 있어도 404 RESOURCE_NOT_FOUND를 돌려준다`() {
        mockMvc
            .get("/api/v2/examples/$OTHER_USER_EXAMPLE") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("RESOURCE_NOT_FOUND") }
            }
    }

    @Test
    fun `일반 팀원이 팀 자원을 삭제하려 하면 403 ACCESS_DENIED를 돌려준다`() {
        mockMvc
            .delete("/api/v2/examples/$TEAM_EXAMPLE") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("ACCESS_DENIED") }
                jsonPath("$.details.required_role") { value("LEADER_OR_DEVOPS") }
                jsonPath("$.details.actor_role") { value("MEMBER") }
            }
    }

    @Test
    fun `팀원은 팀 자원을 볼 수 있지만 permissions의 delete는 false다`() {
        mockMvc
            .get("/api/v2/examples/$TEAM_EXAMPLE") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.permissions.delete") { value(false) }
                jsonPath("$.permissions.extend") { value(true) }
            }
    }

    @Test
    fun `목록은 볼 수 있는 자원만 커서 페이지로 돌려준다`() {
        val first =
            mockMvc
                .get("/api/v2/examples?limit=2") { bearer(tokens.user(ME)) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.items.length()") { value(2) }
                    jsonPath("$.items[0].grn") { value(MY_EXAMPLE) }
                    jsonPath("$.next_cursor") { isNotEmpty() }
                }.andReturn()
        val cursor = Regex("\"next_cursor\":\"([^\"]+)\"").find(first.response.contentAsString)!!.groupValues[1]

        // 남의 개인 자원은 빠지고 팀 자원만 남는다
        mockMvc
            .get("/api/v2/examples?limit=2&cursor=$cursor") { bearer(tokens.user(ME)) }
            .andExpect {
                jsonPath("$.items.length()") { value(1) }
                jsonPath("$.items[0].grn") { value(TEAM_EXAMPLE) }
                jsonPath("$.next_cursor") { doesNotExist() }
            }
    }

    @Test
    fun `도메인 규칙을 어기면 service가 던진 오류를 공통 형식으로 돌려준다`() {
        mockMvc
            .delete("/api/v2/examples/$LOCKED_EXAMPLE") { bearer(tokens.user(ME)) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
                jsonPath("$.message") { value("잠긴 자원은 삭제할 수 없습니다.") }
                jsonPath("$.request_id") { isNotEmpty() }
            }
    }
}
