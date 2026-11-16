package com.gsmsv.api.global.security

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class CurrentUserArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentUser::class.java) &&
            parameter.parameterType == AuthenticatedUser::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): AuthenticatedUser =
        // 인증 없이 여는 경로에 @CurrentUser를 붙인 실수도 익명으로 흘려보내지 않고 401로 막는다
        SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
            ?: throw BusinessException(ErrorCode.UNAUTHENTICATED)
}
