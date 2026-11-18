package com.gsmsv.api.operation.domain

/**
 * 작업 단계 (Contracts-V2 `openapi/operation.yaml`의 `OperationStage`). [value]는 응답에 쓰는 소문자 값이다.
 *
 * - `vm.create`: [QUEUED] → [CLONING] → [CONFIGURING] → [BOOTING] → [NETWORKING]
 * - 그 밖의 작업: [QUEUED] → [RUNNING]
 *
 * 계약 목록에 없는 값은 보내지 않는다. 단계를 더하려면 계약을 먼저 고친다.
 */
enum class OperationStage {
    QUEUED,
    CLONING,
    CONFIGURING,
    BOOTING,
    NETWORKING,
    RUNNING,
    ;

    val value: String
        get() = name.lowercase()

    companion object {
        fun fromValue(value: String): OperationStage? = entries.find { it.value == value }
    }
}
