package com.gsmsv.api.global.authorization

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 엔드포인트의 [RequirePermission] 선언과 로더의 실제 판단을 잇는다.
 *
 * [DeclaredPermissionInterceptor]가 요청에 선언된 동작을 담고, 로더는 판단 전에 [verify]로 같은 동작인지 확인한다.
 * 선언과 다른 동작으로 판단하면 선언이 거짓말이 되므로, 요청을 실패시킨다 (기본 거부).
 */
internal object DeclaredPermission {
    private val ATTRIBUTE = "${DeclaredPermission::class.java.name}.action"

    fun declare(
        request: HttpServletRequest,
        action: Action,
    ) {
        request.setAttribute(ATTRIBUTE, action)
    }

    /**
     * HTTP 요청 안에서 부른 로더라면 [action]이 엔드포인트에 선언한 동작과 같아야 한다.
     * 요청 밖(스케줄러 같은 내부 작업)에서는 선언이 없으므로 검사하지 않는다.
     *
     * @throws IllegalStateException 선언이 없거나 다르면. 개발 중에 드러나야 할 코드 실수라 500으로 나간다
     */
    fun verify(action: Action) {
        val attributes = RequestContextHolder.getRequestAttributes() ?: return
        val declared = attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
        check(declared == action) {
            if (declared == null) {
                "@RequirePermission이 없는 엔드포인트에서 $action 동작으로 로더를 불렀습니다."
            } else {
                "엔드포인트에 선언한 동작($declared)과 로더에 넘긴 동작($action)이 다릅니다."
            }
        }
    }
}

/** 컨트롤러 메서드의 [RequirePermission]을 읽어 요청에 담는다. */
class DeclaredPermissionInterceptor : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler is HandlerMethod) {
            handler.getMethodAnnotation(RequirePermission::class.java)?.let {
                DeclaredPermission.declare(request, it.action)
            }
        }
        return true
    }
}
