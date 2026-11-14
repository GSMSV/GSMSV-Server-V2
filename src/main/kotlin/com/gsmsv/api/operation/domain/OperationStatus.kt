package com.gsmsv.api.operation.domain

/** 작업 상태. 응답에는 소문자로 내보낸다 (예: `running`). **(미정)** 값 목록은 계약이 후속으로 확정한다 */
enum class OperationStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED,
    ;

    val key: String = name.lowercase()

    val isFinished: Boolean
        get() = this == SUCCEEDED || this == FAILED || this == CANCELED
}
