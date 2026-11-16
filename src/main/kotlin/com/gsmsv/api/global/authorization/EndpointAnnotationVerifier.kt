package com.gsmsv.api.global.authorization

import com.gsmsv.api.GsmsvServerV2Application
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * 이 서버의 모든 엔드포인트가 [RequirePermission]·[Authenticated]·[Public] 중 정확히 하나를 달고 있는지 기동할 때 검사한다.
 *
 * 하나라도 어기면 앱이 뜨지 않는다. 규칙을 적지 않은 새 엔드포인트가 배포되지 않게 하는 기본 거부 장치다 (22번 5.1절).
 * Spring이 만든 엔드포인트(`/error` 등)는 이 서버 패키지 밖이라 검사하지 않는다.
 */
class EndpointAnnotationVerifier(
    private val handlerMappings: ObjectProvider<RequestMappingHandlerMapping>,
) : SmartInitializingSingleton {
    override fun afterSingletonsInstantiated() {
        val violations =
            handlerMappings
                .orderedStream()
                .flatMap { it.handlerMethods.values.stream() }
                .toList()
                .mapNotNull(::violationOf)
                .distinct()
                .sorted()
        check(violations.isEmpty()) {
            "엔드포인트마다 @RequirePermission, @Authenticated, @Public 중 하나만 붙여야 합니다.\n" +
                violations.joinToString("\n") { "  - $it" }
        }
    }

    companion object {
        private val APPLICATION_PACKAGE = GsmsvServerV2Application::class.java.packageName

        /** 어긴 엔드포인트면 설명을, 지켰거나 검사 대상이 아니면 null을 돌려준다. */
        internal fun violationOf(handler: HandlerMethod): String? {
            if (!handler.beanType.packageName.startsWith(APPLICATION_PACKAGE)) return null
            val found = ENDPOINT_ANNOTATIONS.filter { handler.hasMethodAnnotation(it.java) }
            if (found.size == 1) return null
            val name = "${handler.beanType.simpleName}.${handler.method.name}"
            return if (found.isEmpty()) {
                "$name: 어노테이션이 없습니다"
            } else {
                "$name: ${found.joinToString { "@${it.simpleName}" }}가 함께 붙어 있습니다"
            }
        }
    }
}
