package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode

/** 권한 판단 결과. */
enum class Decision {
    ALLOW,
    ALLOW_AS_ADMIN,

    /** 볼 수 있지만 권한이 없다. 403 */
    FORBIDDEN,

    /** 볼 수 없다. 존재 여부를 드러내지 않도록 없는 자원과 같은 404 */
    NOT_FOUND,
    ;

    fun throwIfDenied() {
        when (this) {
            ALLOW, ALLOW_AS_ADMIN -> Unit
            FORBIDDEN -> throw BusinessException(ErrorCode.ACCESS_DENIED)
            NOT_FOUND -> throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }
    }
}
