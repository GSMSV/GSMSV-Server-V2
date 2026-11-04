package com.gsmsv.api.global.exception

import com.gsmsv.api.global.response.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        exception: BusinessException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val errorCode = exception.errorCode
        return ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse(errorCode.name, errorCode.message, requestId(request)))
    }

    // MethodArgumentNotValidException도 BindException이라 함께 잡힌다.
    // 어떤 값이 틀렸는지는 메시지에 넣지 않는다. 입력 값을 그대로 되돌려 주지 않으려는 것이다
    @ExceptionHandler(BindException::class)
    fun handleBindException(request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val errorCode = ErrorCode.INVALID_REQUEST
        return ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse(errorCode.name, errorCode.message, requestId(request)))
    }

    // 요청 id 필터가 생기기 전까지는 받은 헤더를 쓰고, 없으면 새로 만든다
    private fun requestId(request: HttpServletRequest): String =
        request.getHeader(REQUEST_ID_HEADER)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

    companion object {
        private const val REQUEST_ID_HEADER = "X-Request-Id"
    }
}
