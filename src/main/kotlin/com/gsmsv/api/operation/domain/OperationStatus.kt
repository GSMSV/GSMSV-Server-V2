package com.gsmsv.api.operation.domain

/**
 * 작업 상태. Infra `JobStatus`와 같은 값이다. [value]는 응답에 쓰는 소문자 값이다.
 *
 * [finished]인 상태는 끝난 상태이고 다시 바뀌지 않는다.
 */
enum class OperationStatus(
    val finished: Boolean,
) {
    QUEUED(false),
    RUNNING(false),
    SUCCEEDED(true),
    FAILED(true),
    CANCELED(true),
    ;

    val value: String
        get() = name.lowercase()

    companion object {
        fun fromValue(value: String): OperationStatus? = entries.find { it.value == value }
    }
}
