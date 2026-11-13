package com.gsmsv.api.network.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** 포트포워딩 하나 (5.6). [kind]·[protocol]은 계약대로 소문자다 */
data class PortForwardResponse(
    val id: String,
    val kind: String,
    val externalPort: Int,
    val internalPort: Int,
    val protocol: String,
    val allowedSource: String?,
    val description: String?,
)

data class PortForwardLimitsResponse(
    val custom: Int,
)

/** 5.6 포트포워딩 목록 */
data class PortForwardListResponse(
    val items: List<PortForwardResponse>,
    val publicHost: String,
    val limits: PortForwardLimitsResponse,
)

/**
 * 5.7 커스텀 포트포워딩 추가. 외부 포트는 받지 않는다 (서버가 고른다).
 *
 * 필수 칸을 nullable로 받아야 빠졌을 때 Jackson 오류(`INVALID_REQUEST`)가 아니라 칸별 검증 오류(`VALIDATION_FAILED`)가 된다.
 */
data class CreatePortForwardRequest(
    @field:NotNull
    @field:Min(1)
    @field:Max(65535)
    val internalPort: Int?,
    @field:NotNull
    @field:Pattern(regexp = "^(tcp|udp)$", message = "tcp 또는 udp여야 합니다.")
    val protocol: String?,
    @field:IpOrCidr
    val allowedSource: String? = null,
    // 계약에 길이 제한이 없다. 메모 용도라 짧게 둔다
    @field:Size(max = 100)
    val description: String? = null,
)

/** 5.7 응답 */
data class CreatePortForwardResponse(
    val id: String,
    val externalPort: Int,
    val internalPort: Int,
    val protocol: String,
    val syncStatus: String,
)
