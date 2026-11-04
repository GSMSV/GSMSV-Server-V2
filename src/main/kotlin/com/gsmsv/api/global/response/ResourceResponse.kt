package com.gsmsv.api.global.response

import java.time.Instant

/**
 * 서비스 자원 응답이 모두 갖는 공통 필드.
 *
 * 각 도메인의 `XxxResponse`가 이 인터페이스를 구현하고, 도메인 고유 필드를 덧붙인다.
 * [status]는 도메인마다 자기 상태 enum으로 좁혀서 선언한다.
 */
interface ResourceResponse {
    val grn: String
    val id: String
    val name: String
    val owner: OwnerResponse
    val status: Enum<*>

    /** 만든 사람의 이름만 준다. 사용자 id는 드러내지 않는다. */
    val createdBy: String
    val createdAt: Instant
    val updatedAt: Instant

    /** 만료가 없는 자원은 null이다. */
    val expiresAt: Instant?

    /** 요청자가 이 자원에 할 수 있는 동작. 프론트는 버튼을 숨기는 데만 쓴다 (22번 9장). */
    val permissions: Map<String, Boolean>
}
