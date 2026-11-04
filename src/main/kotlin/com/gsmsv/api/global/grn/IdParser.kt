package com.gsmsv.api.global.grn

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode

/**
 * GRN이 아닌 id(사용자·팀·하위 자원)의 형식 검사.
 *
 * 로더는 DB를 조회하기 전에 이 검사를 먼저 한다. 형식 오류는 존재 여부를 드러내지 않으므로 400으로 거절해도 안전하다.
 */
object IdParser {
    private val USER_ID = Regex("^${IdPattern.USER_PREFIX}-${IdPattern.LONG}$")
    private val TEAM_ID = Regex("^${IdPattern.TEAM_PREFIX}-${IdPattern.LONG}$")
    private val SUB_RESOURCE_ID = Regex("^(${IdPattern.SUB_RESOURCE_PREFIXES})-${IdPattern.SHORT}$")

    /** @throws BusinessException 형식이 맞지 않으면 [ErrorCode.INVALID_RESOURCE_ID] */
    fun requireUserId(id: String): String = id.also { if (!USER_ID.matches(it)) throw invalid() }

    /** @throws BusinessException 형식이 맞지 않으면 [ErrorCode.INVALID_RESOURCE_ID] */
    fun requireTeamId(id: String): String = id.also { if (!TEAM_ID.matches(it)) throw invalid() }

    /** @throws BusinessException 형식이 맞지 않으면 [ErrorCode.INVALID_RESOURCE_ID] */
    fun parseSubResourceId(id: String): SubResourceId {
        val match = SUB_RESOURCE_ID.matchEntire(id) ?: throw invalid()
        val type = SubResourceType.fromPrefix(match.groupValues[1]) ?: throw invalid()
        return SubResourceId(type, id)
    }

    /**
     * 하위 자원 id를 검사하고, 종류가 [expected]와 같을 때만 돌려준다.
     *
     * @throws BusinessException 형식 오류는 [ErrorCode.INVALID_RESOURCE_ID], 종류가 다르면 [ErrorCode.GRN_RESOURCE_TYPE_MISMATCH]
     */
    fun parseSubResourceId(
        id: String,
        expected: SubResourceType,
    ): SubResourceId {
        val subResourceId = parseSubResourceId(id)
        if (subResourceId.type != expected) {
            throw BusinessException(ErrorCode.GRN_RESOURCE_TYPE_MISMATCH)
        }
        return subResourceId
    }

    private fun invalid() = BusinessException(ErrorCode.INVALID_RESOURCE_ID)
}
