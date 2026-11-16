package com.gsmsv.api.global.authorization

import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * 엔드포인트 권한 어노테이션 설정.
 *
 * WebMvcConfigurer라서 `@WebMvcTest` 슬라이스에도 들어간다. 테스트용 컨트롤러도 어노테이션을 달아야 뜬다.
 */
@Configuration(proxyBeanMethods = false)
class EndpointPermissionConfig : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(DeclaredPermissionInterceptor())
    }

    @Bean
    fun endpointAnnotationVerifier(handlerMappings: ObjectProvider<RequestMappingHandlerMapping>): EndpointAnnotationVerifier =
        EndpointAnnotationVerifier(handlerMappings)
}
