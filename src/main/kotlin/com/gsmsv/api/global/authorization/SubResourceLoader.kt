package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.GrnParser
import com.gsmsv.api.global.grn.IdParser

/** 하위 자원 로더가 돌려주는 부모와 하위 자원. */
data class LoadedSubResource<P : OwnedResource, C : SubResource>(
    val parent: P,
    val child: C,
)

/**
 * 하위 자원 로더 (부모 GRN + 하위 id). 권한은 부모의 소유자로 판단한다.
 *
 * 부모 GRN 없이 하위 id만으로 찾는 길은 두지 않는다 (22번 5.3절).
 *
 * 팀 도메인에 [TeamMembershipReader] 구현이 생기면 Spring 빈으로 등록한다.
 */
class SubResourceLoader(
    private val serviceResourceLoader: ServiceResourceLoader,
) {
    /**
     * @throws BusinessException 하위 id 형식·종류 오류는 400, 부모가 통과하지 못하면 부모 로더의 결과,
     *   하위 자원이 없거나 부모가 다르면 404
     */
    fun <P : OwnedResource, C : SubResource> load(
        actor: Actor,
        parentGrn: String,
        childId: String,
        action: Action,
        parentReader: ServiceResourceReader<P>,
        childReader: SubResourceReader<C>,
    ): LoadedSubResource<P, C> {
        require(parentReader.type in childReader.type.parents) {
            "${childReader.type}는 ${parentReader.type}의 하위 자원이 아닙니다."
        }
        // 형식 검사는 DB 조회 전에 한다
        IdParser.parseSubResourceId(childId, childReader.type)
        val parent = serviceResourceLoader.load(actor, parentGrn, action, parentReader)
        val child = childReader.findById(childId) ?: throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        // 권한이 있는 부모 GRN에 남의 하위 id를 끼워 넣는 요청을 막는다
        if (child.parentId != parent.id) {
            throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }
        return LoadedSubResource(parent, child)
    }

    /**
     * 하위 자원 표기 `{부모 GRN}/{하위 id}`로 불러온다. 표기는 [GrnParser]로만 나눈다.
     *
     * @throws BusinessException 표기 형식 오류는 400, 그 밖에는 위 [load]와 같다
     */
    fun <P : OwnedResource, C : SubResource> load(
        actor: Actor,
        notation: String,
        action: Action,
        parentReader: ServiceResourceReader<P>,
        childReader: SubResourceReader<C>,
    ): LoadedSubResource<P, C> {
        val ref = GrnParser.parseSubResource(notation)
        return load(actor, ref.parentGrn, ref.child.value, action, parentReader, childReader)
    }
}
