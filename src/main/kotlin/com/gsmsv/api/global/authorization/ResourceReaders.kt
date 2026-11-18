package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.grn.ResourceType
import com.gsmsv.api.global.grn.SubResourceType

/** GRN을 갖는 서비스 자원. [owner]는 만든 뒤 바꾸지 않는다 (엔티티에서 `updatable = false`로 매핑한다). */
interface OwnedResource {
    val id: String
    val owner: Owner
}

/** 부모 서비스 자원 아래의 하위 자원. [parentId]는 DB에 저장된 실제 부모 id다. */
interface SubResource {
    val id: String
    val parentId: String
}

/** 팀 전용 로더가 다루는 팀. [approved]는 관리자가 팀 생성을 승인했는지다 (승인 대기·거절이면 false). */
interface TeamResource {
    val id: String
    val approved: Boolean
}

/** 서비스 자원 조회 포트. 각 도메인이 구현하고, 로더와 `listVisible` 밖에서는 부르지 않는다 (22번 규칙). */
interface ServiceResourceReader<T : OwnedResource> {
    val type: ResourceType

    fun findById(id: String): T?

    fun findAll(): List<T>

    fun findAllByOwners(owners: Collection<Owner>): List<T>
}

/** 하위 자원 조회 포트. 하위 자원 로더 밖에서는 부르지 않는다. */
interface SubResourceReader<C : SubResource> {
    val type: SubResourceType

    fun findById(id: String): C?
}

/**
 * 오래 걸리는 작업(operation) 기록. 자원이 아니라 자원에 대한 작업이라 권한은 대상 자원의 소유자로 판단한다.
 *
 * [targetOwner]는 등록할 때 로더가 돌려준 대상 자원의 DB 소유자를 복사한 값이다. 자원의 소유자는 바뀌지 않으므로
 * 대상 자원이 삭제된 뒤에도 같은 값으로 판단할 수 있다.
 */
interface OperationRecord {
    val id: String
    val targetOwner: Owner
}

/** operation 조회 포트. 작업 로더 밖에서는 부르지 않는다. 보관 기간이 지난 기록은 돌려주지 않는다. */
interface OperationReader<T : OperationRecord> {
    fun findById(id: String): T?
}

/** 팀 조회 포트. 팀 전용 로더 밖에서는 부르지 않는다. */
interface TeamReader<T : TeamResource> {
    fun findById(id: String): T?
}
