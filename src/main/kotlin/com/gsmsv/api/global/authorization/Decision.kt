package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode

/** 권한 판단 결과. */
sealed interface Decision {
    data object Allow : Decision

    data object AllowAsAdmin : Decision

    /**
     * 볼 수 있지만 권한이 없다. 403
     *
     * 응답에 필요한 범위([required])와 요청자의 역할([actorRole])을 담는다 (22번 7장).
     * [actorRole]은 팀 역할이 없는 경우(개인 자원 소유자) null이다
     */
    data class Forbidden(
        val required: Grant,
        val actorRole: TeamRole?,
    ) : Decision

    /** 권한은 있지만 팀이 승인되지 않았다. 403 (21번 4.1절) */
    data object TeamNotApproved : Decision

    /** 볼 수 없다. 존재 여부를 드러내지 않도록 없는 자원과 같은 404 */
    data object NotFound : Decision

    val isAllowed: Boolean
        get() = this == Allow || this == AllowAsAdmin

    fun throwIfDenied() {
        when (this) {
            Allow, AllowAsAdmin -> Unit
            is Forbidden -> throw forbidden()
            TeamNotApproved -> throw BusinessException(ErrorCode.TEAM_NOT_APPROVED)
            NotFound -> throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }
    }
}

// details 키 이름은 Contracts-V2가 아직 정하지 않았다 (api-rules 3장 공통 에러 code). 정해지면 맞춘다
private fun Decision.Forbidden.forbidden(): BusinessException {
    val roleText = actorRole?.let { " 내 역할: ${it.description}" } ?: ""
    return BusinessException(
        ErrorCode.ACCESS_DENIED,
        details =
            buildMap {
                put("required_role", required.name)
                actorRole?.let { put("actor_role", it.name) }
            },
        message = "${required.description}만 할 수 있습니다.$roleText",
    )
}
