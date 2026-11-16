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

/** 팀 조회 포트. 팀 전용 로더 밖에서는 부르지 않는다. */
interface TeamReader<T : TeamResource> {
    fun findById(id: String): T?
}
