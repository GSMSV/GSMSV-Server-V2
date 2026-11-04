package com.gsmsv.api.global.grn

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode

/**
 * 형식 검사를 통과한 서비스 자원 GRN. [GrnParser]로만 만들 수 있다.
 *
 * 컨트롤러는 경로 변수 `{grn}`을 이 타입으로 받는다 ([StringToGrnConverter]).
 * [value]는 요청에 적힌 GRN 전체이고, 로더가 DB 값으로 만든 GRN과 통째로 비교하는 데 쓴다.
 */
class Grn private constructor(
    val value: String,
    val resourceId: ResourceId,
) {
    /**
     * 자원 종류가 [expected]일 때만 자원 id를 돌려준다.
     *
     * @throws BusinessException 종류가 다르면 [ErrorCode.GRN_RESOURCE_TYPE_MISMATCH]
     */
    fun requireType(expected: ResourceType): ResourceId {
        if (resourceId.type != expected) {
            throw BusinessException(ErrorCode.GRN_RESOURCE_TYPE_MISMATCH)
        }
        return resourceId
    }

    override fun equals(other: Any?): Boolean = other is Grn && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        /** @throws BusinessException 형식이 맞지 않으면 [ErrorCode.INVALID_GRN] */
        fun parse(value: String): Grn = Grn(value, GrnParser.parse(value))
    }
}
