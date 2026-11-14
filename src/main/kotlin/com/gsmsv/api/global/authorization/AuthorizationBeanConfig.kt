package com.gsmsv.api.global.authorization

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class AuthorizationBeanConfig {
    @Bean
    fun authorizationAuditLogger() = AuthorizationAuditLogger()

    @Bean
    fun authorizationPolicy(teamMembershipReader: TeamMembershipReader) = AuthorizationPolicy(teamMembershipReader)

    @Bean
    fun serviceResourceLoader(
        policy: AuthorizationPolicy,
        auditLogger: AuthorizationAuditLogger,
    ) = ServiceResourceLoader(policy, auditLogger)

    @Bean
    fun subResourceLoader(serviceResourceLoader: ServiceResourceLoader) = SubResourceLoader(serviceResourceLoader)

    @Bean
    fun teamLoader(
        policy: AuthorizationPolicy,
        auditLogger: AuthorizationAuditLogger,
    ) = TeamLoader(policy, auditLogger)

    @Bean
    fun platformLoader(
        policy: AuthorizationPolicy,
        auditLogger: AuthorizationAuditLogger,
    ) = PlatformLoader(policy, auditLogger)

    @Bean
    fun permissionsResolver(policy: AuthorizationPolicy) = PermissionsResolver(policy)
}
