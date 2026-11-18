package com.gsmsv.api.operation.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 오래 걸리는 작업 설정.
 *
 * - [retention]: 끝난 작업을 조회할 수 있는 기간 (Contracts-V2 `openapi/operation.yaml`, 30일)
 * - [streamHeartbeat]: SSE 연결을 유지하려고 주석 줄을 보내는 간격 (계약: 15초)
 * - [streamTimeout]: SSE 연결 하나를 열어 두는 최대 시간. 끊기면 화면이 다시 연결해 지금 상태를 받는다
 */
@ConfigurationProperties("gsmsv.operation")
data class OperationProperties(
    val retention: Duration = Duration.ofDays(30),
    val streamHeartbeat: Duration = Duration.ofSeconds(15),
    val streamTimeout: Duration = Duration.ofMinutes(30),
)
