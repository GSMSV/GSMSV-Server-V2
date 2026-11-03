package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode

/** 하위 자원 로더가 돌려주는 부모와 하위 자원. */
data class LoadedSubResource<P : OwnedResource, C : SubResource>(
    val parent: P,
    val child: C,
)

/**
 * 하위 자원 로더 (부모 GRN + 하위 id). 권한은 부모의 소유자로 판단한다.
 *
 * 팀 도메인에 [TeamMembershipReader] 구현이 생기면 Spring 빈으로 등록한다.
 */
class SubResourceLoader(
    private val serviceResourceLoader: ServiceResourceLoader,
) {
    /**
     * @throws BusinessException 부모가 통과하지 못하면 부모 로더의 결과, 하위 자원이 없거나 부모가 다르면 404
     */
    fun <P : OwnedResource, C : SubResource> load(
        actor: Actor,
        parentGrn: String,
        childId: String,
        action: Action,
        parentReader: ServiceResourceReader<P>,
        childReader: SubResourceReader<C>,
    ): LoadedSubResource<P, C> {
        val parent = serviceResourceLoader.load(actor, parentGrn, action, parentReader)
        val child = childReader.findById(childId) ?: throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        // 권한이 있는 부모 GRN에 남의 하위 id를 끼워 넣는 요청을 막는다
        if (child.parentId != parent.id) {
            throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }
        return LoadedSubResource(parent, child)
    }
}
