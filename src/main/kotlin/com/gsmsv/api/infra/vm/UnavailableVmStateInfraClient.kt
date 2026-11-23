package com.gsmsv.api.infra.vm

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.infra.config.InfraProfiles
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Go 실시간 상태 API가 정해지기 전의 운영용 구현. 모의 값을 운영에 내보내지 않으려고 항상 실패한다.
 * 목록·상세는 이 실패를 `power_state: unknown`으로 바꿔 준다. Go 명세가 정해지면 HTTP 클라이언트로 바꾼다.
 */
@Component
@Profile(InfraProfiles.REAL)
class UnavailableVmStateInfraClient : VmStateInfraClient {
    override fun getState(vmId: String): VmLiveState? = throw BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE)
}
