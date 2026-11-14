package com.gsmsv.api.network.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** 5.11 요청. 빠진 칸도 400 `VALIDATION_FAILED`로 알리려고 nullable로 받고 Bean Validation으로 막는다 */
data class CreateDomainRequest(
    @field:NotNull
    @field:Size(min = 1, max = 63)
    @field:Pattern(regexp = "^[a-z0-9-]+$", message = "영문 소문자·숫자·하이픈(-)만 쓸 수 있습니다.")
    val subdomain: String?,
    @field:NotNull
    @field:Min(1)
    @field:Max(65535)
    val internalPort: Int?,
)

data class DomainResponse(
    val id: String,
    val subdomain: String,
    val url: String,
    val internalPort: Int,
    val syncStatus: String,
)

data class DomainLimits(
    val perVm: Int,
)

/** 5.10 응답 */
data class DomainListResponse(
    val items: List<DomainResponse>,
    val limits: DomainLimits,
)

/** 5.11 응답 */
data class CreateDomainResponse(
    val id: String,
    val url: String,
    val syncStatus: String,
)
