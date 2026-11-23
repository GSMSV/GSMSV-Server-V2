package com.gsmsv.api.team.service

import com.gsmsv.api.app.service.AppQuotaService
import com.gsmsv.api.database.service.DatabaseQuotaService
import com.gsmsv.api.global.config.QuotaProperties
import com.gsmsv.api.quota.dto.UsageAmountResponse
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.dto.TeamAppUsageResponse
import com.gsmsv.api.team.dto.TeamDatabaseUsageResponse
import com.gsmsv.api.team.dto.TeamUsageResponse
import com.gsmsv.api.team.dto.TeamVmUsageResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TeamUsageService(
    private val quotaProperties: QuotaProperties,
    private val teamVmUsageReader: TeamVmUsageReader,
    private val databaseQuotaService: DatabaseQuotaService,
    private val appQuotaService: AppQuotaService,
) {
    @Transactional(readOnly = true)
    fun get(team: Team): TeamUsageResponse {
        val vmUsage = teamVmUsageReader.readUsage(team.id)
        val databaseUsage = databaseQuotaService.teamUsage(team.id)
        val appUsage = appQuotaService.teamUsage(team.id)
        val teamLimit = quotaProperties.team

        return TeamUsageResponse(
            teamId = team.id,
            vm =
                TeamVmUsageResponse(
                    vcpu = UsageAmountResponse(vmUsage.vcpu, teamLimit.vcpu),
                    ramGb = UsageAmountResponse(vmUsage.ramGib, teamLimit.ramGib),
                    ssdGb = UsageAmountResponse(vmUsage.ssdGib, teamLimit.ssdGib),
                ),
            db =
                TeamDatabaseUsageResponse(
                    count = UsageAmountResponse(databaseUsage.countUsed, databaseUsage.countLimit),
                    dedicated = UsageAmountResponse(databaseUsage.dedicatedUsed, databaseUsage.dedicatedLimit),
                    storageGb =
                        UsageAmountResponse(databaseUsage.sharedStorageUsedGib, databaseUsage.sharedStorageLimitGib),
                    writeLocked = databaseUsage.writeLocked,
                ),
            app =
                TeamAppUsageResponse(
                    count = UsageAmountResponse(appUsage.countUsed, appUsage.countLimit),
                    memoryMb = UsageAmountResponse(appUsage.memoryUsedMib, appUsage.memoryLimitMib),
                    alwaysOn = UsageAmountResponse(appUsage.alwaysOnUsed, appUsage.alwaysOnLimit),
                ),
            updatedAt = null,
        )
    }
}
