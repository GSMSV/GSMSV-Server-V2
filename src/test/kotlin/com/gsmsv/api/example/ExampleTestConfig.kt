package com.gsmsv.api.example

import com.gsmsv.api.example.domain.Example
import com.gsmsv.api.example.repository.InMemoryExampleReader
import com.gsmsv.api.example.service.ExampleService
import com.gsmsv.api.global.authorization.AuthorizationAuditLogger
import com.gsmsv.api.global.authorization.AuthorizationPolicy
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.PermissionsResolver
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.ServiceResourceReader
import com.gsmsv.api.global.authorization.TeamMembershipReader
import com.gsmsv.api.global.authorization.TeamRole
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.time.Instant

/**
 * 예시 컨트롤러 테스트의 빈 구성.
 *
 * 로더·[PermissionsResolver]는 팀 도메인의 [TeamMembershipReader] 구현이 생기기 전까지 Spring 빈이 아니라서 여기서 직접 만든다.
 * 팀 도메인이 생기면 이 부분은 실제 빈으로 바뀌고, 테스트는 reader만 가짜로 넣으면 된다.
 */
@TestConfiguration(proxyBeanMethods = false)
class ExampleTestConfig {
    @Bean
    fun teamMembershipReader(): TeamMembershipReader = FakeMemberships

    @Bean
    fun authorizationPolicy(teamMembershipReader: TeamMembershipReader) = AuthorizationPolicy(teamMembershipReader)

    @Bean
    fun authorizationAuditLogger() = AuthorizationAuditLogger()

    @Bean
    fun serviceResourceLoader(
        policy: AuthorizationPolicy,
        auditLogger: AuthorizationAuditLogger,
    ) = ServiceResourceLoader(policy, auditLogger)

    @Bean
    fun permissionsResolver(policy: AuthorizationPolicy) = PermissionsResolver(policy)

    @Bean
    fun exampleReader(): ServiceResourceReader<Example> = InMemoryExampleReader(EXAMPLES)

    @Bean
    fun exampleService(
        loader: ServiceResourceLoader,
        reader: ServiceResourceReader<Example>,
        permissionsResolver: PermissionsResolver,
    ) = ExampleService(loader, reader, permissionsResolver)

    /** [TEAM]에서 [ME]는 일반 팀원이다 */
    private object FakeMemberships : TeamMembershipReader {
        override fun findRole(
            teamId: String,
            userId: String,
        ): TeamRole? = if (teamId == TEAM && userId == ME) TeamRole.MEMBER else null

        override fun findMemberships(userId: String): Map<String, TeamRole> {
            val role = findRole(TEAM, userId) ?: return emptyMap()
            return mapOf(TEAM to role)
        }
    }

    companion object {
        const val ME = "user-a1b2c3d4e5f6"
        const val OTHER_USER = "user-b2c3d4e5f6g7"
        const val TEAM = "team-c3d4e5f6g7h8"

        const val MY_EXAMPLE = "grn:$ME:vm-k3m9x2p7q4ta"
        const val LOCKED_EXAMPLE = "grn:$ME:vm-h2j4k6m8n3pc"
        const val OTHER_USER_EXAMPLE = "grn:$OTHER_USER:vm-m4n8p2q6r3sb"
        const val TEAM_EXAMPLE = "grn:$TEAM:vm-t5v7w9x2y4za"

        private val NOW = Instant.parse("2026-01-01T00:00:00Z")

        private val EXAMPLES =
            listOf(
                Example("vm-k3m9x2p7q4ta", Owner.User(ME), "내 자원", "나", "나", NOW),
                Example("vm-h2j4k6m8n3pc", Owner.User(ME), "잠긴 자원", "나", "나", NOW.minusSeconds(60), locked = true),
                Example("vm-m4n8p2q6r3sb", Owner.User(OTHER_USER), "남의 자원", "남", "남", NOW.minusSeconds(120)),
                Example("vm-t5v7w9x2y4za", Owner.Team(TEAM), "팀 자원", "우리 팀", "팀장", NOW.minusSeconds(180)),
            )
    }
}
