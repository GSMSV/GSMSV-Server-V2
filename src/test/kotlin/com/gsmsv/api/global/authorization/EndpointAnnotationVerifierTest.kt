package com.gsmsv.api.global.authorization

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.support.StaticApplicationContext
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EndpointAnnotationVerifierTest {
    class Endpoints {
        @RequirePermission(Action.DELETE)
        fun permission() = Unit

        @Authenticated
        fun authenticated() = Unit

        @Public
        fun public() = Unit

        fun none() = Unit

        @Public
        @Authenticated
        fun both() = Unit
    }

    private fun handler(name: String) = HandlerMethod(Endpoints(), Endpoints::class.java.getMethod(name))

    @Test
    fun `세 어노테이션 중 하나만 붙어 있으면 통과한다`() {
        listOf("permission", "authenticated", "public").forEach {
            assertNull(EndpointAnnotationVerifier.violationOf(handler(it)), it)
        }
    }

    @Test
    fun `어노테이션이 없으면 위반이다`() {
        assertEquals("Endpoints.none: 어노테이션이 없습니다", EndpointAnnotationVerifier.violationOf(handler("none")))
    }

    @Test
    fun `두 개 이상 붙어 있으면 위반이다`() {
        val violation = assertNotNull(EndpointAnnotationVerifier.violationOf(handler("both")))
        assertContains(violation, "@Authenticated")
        assertContains(violation, "@Public")
    }

    @Test
    fun `이 서버 패키지 밖의 엔드포인트는 검사하지 않는다`() {
        assertNull(EndpointAnnotationVerifier.violationOf(HandlerMethod("", String::class.java.getMethod("length"))))
    }

    @Test
    fun `위반한 엔드포인트가 있으면 기동할 때 실패한다`() {
        val mapping =
            RequestMappingHandlerMapping().apply {
                applicationContext = StaticApplicationContext()
                afterPropertiesSet()
                registerMapping(
                    RequestMappingInfo
                        .paths("/none")
                        .build(),
                    Endpoints(),
                    Endpoints::class.java.getMethod("none"),
                )
            }

        val error = assertThrows<IllegalStateException> { EndpointAnnotationVerifier(providerOf(mapping)).afterSingletonsInstantiated() }

        assertContains(error.message.orEmpty(), "Endpoints.none")
    }

    private fun providerOf(mapping: RequestMappingHandlerMapping): ObjectProvider<RequestMappingHandlerMapping> =
        StaticApplicationContext()
            .apply {
                beanFactory.registerSingleton("mapping", mapping)
                refresh()
            }.getBeanProvider(RequestMappingHandlerMapping::class.java)
}
