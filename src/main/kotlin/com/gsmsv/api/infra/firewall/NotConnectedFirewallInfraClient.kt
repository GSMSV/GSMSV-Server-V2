package com.gsmsv.api.infra.firewall

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.infra.config.InfraProfiles
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/**
 * `dev`·`local`이 아닌 프로필의 자리 표시 구현. Go 방화벽 내부 API가 계약에 아직 없어서 실제로 보낼 곳이 없다.
 *
 * 반영하지 않은 채 성공으로 답하면 사용자가 막았다고 믿는 포트가 열려 있게 되므로, 언제나 503으로 실패시킨다.
 * Go 명세가 정해지면 `GoApiCaller`로 부르는 실제 구현으로 바꾼다.
 */
@Component
@Profile(InfraProfiles.REAL)
class NotConnectedFirewallInfraClient : FirewallInfraClient {
    override fun syncVmFirewall(
        vmId: String,
        internalIp: String,
        enabled: Boolean,
        rules: List<VmFirewallRule>,
    ): FirewallSyncResult {
        log.warn { "Go 방화벽 내부 API가 아직 연결되지 않았다: vmId=$vmId" }
        throw BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE)
    }
}
