package com.gsmsv.api.team.dto

import com.gsmsv.api.quota.dto.UsageAmountResponse
import java.time.Instant

data class TeamUsageResponse(
    val teamId: String,
    val vm: TeamVmUsageResponse,
    val db: TeamDatabaseUsageResponse,
    val app: TeamAppUsageResponse,
    val updatedAt: Instant?,
)

data class TeamVmUsageResponse(
    val vcpu: UsageAmountResponse<Int>,
    val ramGb: UsageAmountResponse<Int>,
    val ssdGb: UsageAmountResponse<Int>,
)

data class TeamDatabaseUsageResponse(
    val count: UsageAmountResponse<Long>,
    val dedicated: UsageAmountResponse<Long>,
    val storageGb: UsageAmountResponse<Double>,
    val writeLocked: Boolean,
)

data class TeamAppUsageResponse(
    val count: UsageAmountResponse<Long>,
    val memoryMb: UsageAmountResponse<Long>,
    val alwaysOn: UsageAmountResponse<Long>,
)
