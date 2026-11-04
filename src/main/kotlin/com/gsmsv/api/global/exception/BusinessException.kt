package com.gsmsv.api.global.exception

/** [message]를 주지 않으면 [ErrorCode.message]를 쓴다. 응답 message에 그대로 나가므로 요청자가 보내지 않은 자원 정보를 담지 않는다 */
class BusinessException(
    val errorCode: ErrorCode,
    val details: Map<String, Any?>? = null,
    override val message: String = errorCode.message,
) : RuntimeException(message)
