package com.gsmsv.api.global.config

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * 사용량 한도 설정 (Contracts-V2 `policies/quota.md` 3·4장).
 *
 * [personal]은 일반 사용자 1인의 개인 VM 할당량 합계 상한, [team]은 팀 하나의 팀 VM 할당량 합계 상한이다.
 */
@Validated
@ConfigurationProperties("gsmsv.quota")
data class QuotaProperties(
    @field:Valid val personal: ResourceQuota,
    @field:Valid val team: ResourceQuota,
)

/**
 * CPU·RAM·SSD 할당량. 세 자원을 따로 센다. RAM과 SSD는 1024 단위(GiB)다.
 */
data class ResourceQuota(
    @field:Positive val vcpu: Int,
    @field:Positive val ramGib: Int,
    @field:Positive val ssdGib: Int,
)
