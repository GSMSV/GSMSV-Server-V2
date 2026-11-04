package com.gsmsv.api.global.exception

class BusinessException(
    val errorCode: ErrorCode,
    val details: Map<String, Any?>? = null,
) : RuntimeException(errorCode.message)
