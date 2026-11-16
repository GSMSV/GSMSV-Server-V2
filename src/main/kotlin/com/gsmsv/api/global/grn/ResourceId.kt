package com.gsmsv.api.global.grn

/**
 * GRN에서 꺼낸 서비스 자원 id. [value]는 접두사를 포함한 전체 id다 (예: `vm-k3m9x2p7q4ta`).
 *
 * 소유자 칸 값은 일부러 담지 않는다. 권한 판단은 DB의 소유자로만 한다 (23번 규칙).
 */
data class ResourceId(
    val type: ResourceType,
    val value: String,
)

/** 형식 검사를 통과한 하위 자원 id. [value]는 접두사를 포함한 전체 id다 (예: `snap-7qz3m8k2`). */
data class SubResourceId(
    val type: SubResourceType,
    val value: String,
)

/**
 * 하위 자원 표기 `{부모 GRN}/{하위 id}`를 파서로 나눈 결과.
 *
 * [parentGrn]은 요청에 적힌 부모 GRN 전체다. 서비스 자원 로더가 DB 값으로 만든 GRN과 통째로 비교하는 데만 쓴다.
 * 소유자 칸 값은 따로 담지 않는다.
 */
data class SubResourceRef(
    val parentGrn: String,
    val parent: ResourceId,
    val child: SubResourceId,
)
