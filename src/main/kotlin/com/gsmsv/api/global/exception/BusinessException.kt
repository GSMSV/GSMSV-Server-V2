package com.gsmsv.api.global.exception

class BusinessException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)
