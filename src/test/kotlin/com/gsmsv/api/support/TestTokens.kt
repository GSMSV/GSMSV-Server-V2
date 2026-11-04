package com.gsmsv.api.support

import com.gsmsv.api.global.security.AccessTokenProvider
import com.gsmsv.api.global.security.Role
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockHttpServletRequestDsl

/**
 * 테스트에서 쓸 액세스 토큰을 만든다. 개발용 토큰 API(`POST /api/v2/dev/token`)와 같은 [AccessTokenProvider.issue]로 발급하므로
 * 실제 인증 필터를 그대로 통과한다. 사용자가 DB에 있는지는 보지 않는다.
 *
 * [ApiWebMvcTest]가 빈으로 넣는다.
 */
class TestTokens(
    private val tokenProvider: AccessTokenProvider,
) {
    fun user(userId: String): String = tokenProvider.issue(userId, Role.USER).value

    fun admin(userId: String): String = tokenProvider.issue(userId, Role.ADMIN).value

    companion object {
        /** 테스트 전용 서명 키. 실제 환경의 값이 아니다 */
        const val SECRET = "dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="
    }
}

/** MockMvc 요청에 `Authorization: Bearer` 헤더를 붙인다. 예: `mockMvc.get("/api/v2/vms") { bearer(tokens.user(ME)) }` */
fun MockHttpServletRequestDsl.bearer(token: String) {
    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
}
