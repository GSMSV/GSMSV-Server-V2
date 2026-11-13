package com.gsmsv.api.quota.dto

data class MyQuotaResponse(
    val vm: MyVmQuotaResponse,
    val db: MyDbQuotaResponse,
    val fn: MyFnQuotaResponse,
    val app: MyAppQuotaResponse,
)

/** 칸 이름은 `_gb`지만 값은 GiB다 (Contracts-V2 `policies/quota.md` 5장) */
data class MyVmQuotaResponse(
    val vcpu: VmQuotaUsageResponse,
    val ramGb: VmQuotaUsageResponse,
    val ssdGb: VmQuotaUsageResponse,
    val maxCreatable: MaxCreatableResponse,
)

/** 관리자는 1인 한도가 없어서 [limit]이 null이다 */
data class VmQuotaUsageResponse(
    val used: Int,
    val limit: Int?,
)

/** 노드 값을 받기 전 관리자는 최대값을 몰라서 모두 null이다 */
data class MaxCreatableResponse(
    val vcpu: Int?,
    val ramGb: Int?,
    val ssdGb: Int?,
)

data class MyDbQuotaResponse(
    val count: CountUsageResponse,
    val sharedStorageGb: StorageUsageResponse,
)

data class MyFnQuotaResponse(
    val count: CountUsageResponse,
    val dailyExecutions: CountUsageResponse,
)

data class MyAppQuotaResponse(
    val count: CountUsageResponse,
    val memoryMb: CountUsageResponse,
    val alwaysOn: CountUsageResponse,
)

data class CountUsageResponse(
    val used: Long,
    val limit: Long,
)

data class StorageUsageResponse(
    val used: Double,
    val limit: Double,
)
