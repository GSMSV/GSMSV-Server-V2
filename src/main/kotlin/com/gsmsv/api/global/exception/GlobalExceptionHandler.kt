package com.gsmsv.api.global.exception

import com.gsmsv.api.global.config.RequestIdFilter
import com.gsmsv.api.global.response.ErrorResponse
import com.gsmsv.api.global.response.FieldErrorDetail
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.MessageSourceResolvable
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.validation.method.ParameterErrors
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.exc.UnrecognizedPropertyException

// 부모 클래스의 logger 필드와 겹치지 않게 이름을 다르게 둔다
private val log = KotlinLogging.logger {}

/**
 * 모든 오류를 `{code, message, request_id, details}` 형식으로 돌려준다.
 * Spring MVC가 던지는 예외는 [ResponseEntityExceptionHandler]가 받아 [handleExceptionInternal]로 모은다.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        exception: BusinessException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> = errorResponse(exception.errorCode, requestIdOf(request), exception.details, exception.message)

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(
        exception: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.error(exception) { "처리하지 못한 예외" }
        // 내부 구조가 드러나지 않도록 예외 메시지와 스택 트레이스는 응답에 넣지 않는다
        return errorResponse(ErrorCode.INTERNAL, requestIdOf(request))
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val requestId = requestIdOf(request)
        val response =
            when (ex) {
                is MethodArgumentNotValidException -> {
                    validationFailed(ex.bindingResult.allErrors.map(::toFieldDetail), requestId)
                }

                is HandlerMethodValidationException -> {
                    validationFailed(parameterErrors(ex), requestId)
                }

                is HttpMessageNotReadableException -> {
                    notReadable(ex, requestId)
                }

                else -> {
                    if (statusCode.is5xxServerError) log.error(ex) { "처리하지 못한 예외" }
                    // 상태 코드는 Spring이 정한 값을 따른다 (406, 413처럼 따로 code를 두지 않은 상태도 있다)
                    errorResponse(errorCodeOf(ex, statusCode), requestId).let { ResponseEntity.status(statusCode).body(it.body) }
                }
            }
        // 405의 Allow처럼 Spring이 정한 헤더는 그대로 둔다
        return ResponseEntity.status(response.statusCode).headers(headers).body(response.body)
    }

    private fun validationFailed(
        fields: List<FieldErrorDetail>,
        requestId: String,
    ): ResponseEntity<ErrorResponse> = errorResponse(ErrorCode.VALIDATION_FAILED, requestId, mapOf("fields" to fields))

    private fun notReadable(
        exception: HttpMessageNotReadableException,
        requestId: String,
    ): ResponseEntity<ErrorResponse> {
        val unknown =
            generateSequence<Throwable>(exception) { it.cause }
                .filterIsInstance<UnrecognizedPropertyException>()
                .firstOrNull()
                ?: return errorResponse(ErrorCode.INVALID_REQUEST, requestId)
        // Jackson이 만든 경로는 이미 요청 JSON의 이름(snake_case)이다
        val field =
            unknown.path
                .joinToString(".") { it.propertyName ?: "[${it.index}]" }
                .replace(".[", "[")
        return errorResponse(ErrorCode.UNKNOWN_FIELD, requestId, mapOf("field" to field))
    }

    private fun parameterErrors(exception: HandlerMethodValidationException): List<FieldErrorDetail> =
        exception.parameterValidationResults.flatMap { result ->
            if (result is ParameterErrors) {
                result.allErrors.map(::toFieldDetail)
            } else {
                result.resolvableErrors.map { FieldErrorDetail(result.methodParameter.parameterName, reasonOf(it)) }
            }
        }

    private fun toFieldDetail(error: MessageSourceResolvable): FieldErrorDetail =
        FieldErrorDetail((error as? FieldError)?.field?.let(::toJsonPath), reasonOf(error))

    private fun reasonOf(error: MessageSourceResolvable): String = error.defaultMessage ?: ErrorCode.VALIDATION_FAILED.message

    private fun errorCodeOf(
        exception: Exception,
        statusCode: HttpStatusCode,
    ): ErrorCode =
        when {
            exception is NoResourceFoundException || exception is NoHandlerFoundException -> ErrorCode.ENDPOINT_NOT_FOUND
            exception is HttpRequestMethodNotSupportedException -> ErrorCode.METHOD_NOT_ALLOWED
            exception is HttpMediaTypeNotSupportedException -> ErrorCode.UNSUPPORTED_MEDIA_TYPE
            statusCode.is4xxClientError -> ErrorCode.INVALID_REQUEST
            else -> ErrorCode.INTERNAL
        }

    private fun errorResponse(
        errorCode: ErrorCode,
        requestId: String,
        details: Map<String, Any?>? = null,
        message: String = errorCode.message,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(errorCode.status)
            .body(ErrorResponse(errorCode.name, message, requestId, details))

    private fun requestIdOf(request: HttpServletRequest): String =
        request.getAttribute(RequestIdFilter.ATTRIBUTE) as? String ?: RequestIdFilter.generate()

    private fun requestIdOf(request: WebRequest): String =
        request.getAttribute(RequestIdFilter.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) as? String
            ?: RequestIdFilter.generate()

    companion object {
        private val SNAKE_CASE = PropertyNamingStrategies.SNAKE_CASE
        private val NAME_SEGMENT = Regex("[A-Za-z0-9_]+")

        // 검증 오류의 필드 이름은 Kotlin 프로퍼티 이름(camelCase)이라 요청 JSON 이름으로 바꾼다
        private fun toJsonPath(field: String): String = field.replace(NAME_SEGMENT) { SNAKE_CASE.nameForField(null, null, it.value) }
    }
}
