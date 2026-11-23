package com.gsmsv.api.vm.dto

import com.gsmsv.api.global.response.OwnerResponse
import com.gsmsv.api.network.dto.DomainResponse
import java.time.Instant

/** 4.5 목록 항목 (Contracts-V2 `VmSummary`) */
data class VmSummaryResponse(
    val grn: String,
    val id: String,
    val name: String,
    val owner: OwnerResponse,
    val status: String,
    val powerState: String,
    val image: String,
    val spec: VmSpecResponse,
    val usage: VmUsageResponse?,
    val internalIp: String?,
    val purpose: String?,
    val createdAt: Instant,
    val expiresAt: Instant,
    val permissions: Map<String, Boolean>,
)

/**
 * 4.6 상세 (Contracts-V2 `VmDetail`).
 *
 * 스냅샷·포트포워딩·방화벽 기능이 아직 없어 그 목록은 언제나 빈 배열이고, `ssh`는 SSH 포트포워딩이 없으니 null이다.
 * 기능이 생기면 원소 타입을 그 도메인의 응답 DTO로 바꾼다.
 */
data class VmDetailResponse(
    val grn: String,
    val id: String,
    val name: String,
    val owner: OwnerResponse,
    val status: String,
    val powerState: String,
    val image: String,
    val spec: VmSpecResponse,
    val usage: VmUsageResponse?,
    val internalIp: String?,
    val purpose: String?,
    val initialized: Boolean,
    val ssh: VmSshResponse?,
    val autoSnapshot: Boolean,
    val snapshots: List<Nothing>,
    val portForwards: List<Nothing>,
    val domains: List<DomainResponse>,
    val firewallRules: List<Nothing>,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant,
    val permissions: Map<String, Boolean>,
)

/** 칸 이름은 `_gb`지만 값은 GiB다 (Contracts-V2 `policies/quota.md` 5장) */
data class VmSpecResponse(
    val vcpu: Int,
    val ramGb: Int,
    val ssdGb: Int,
)

data class VmUsageResponse(
    val cpuPercent: Double,
    val memoryPercent: Double,
    val uptimeSec: Long,
)

data class VmSshResponse(
    val user: String,
    val host: String,
    val port: Int,
    val command: String,
)
