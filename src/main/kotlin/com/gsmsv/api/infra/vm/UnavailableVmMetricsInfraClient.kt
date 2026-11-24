package com.gsmsv.api.infra.vm

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.infra.config.InfraProfiles
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Go VM 지표 API(13.9)가 정해지기 전의 운영용 구현. 모의 값을 운영에 내보내지 않으려고 항상 실패한다 (503).
 * Go 명세가 정해지면 HTTP 클라이언트로 바꾼다.
 */
@Component
@Profile(InfraProfiles.REAL)
class UnavailableVmMetricsInfraClient : VmMetricsInfraClient {
    override fun getMetrics(
        vmId: String,
        period: VmMetricsPeriod,
    ): List<VmMetricSample> = throw BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE)
}
