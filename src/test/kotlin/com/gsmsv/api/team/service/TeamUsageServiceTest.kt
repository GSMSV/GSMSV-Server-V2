package com.gsmsv.api.team.service

import com.gsmsv.api.app.service.AppQuotaService
import com.gsmsv.api.app.service.AppQuotaUsage
import com.gsmsv.api.database.service.DatabaseQuotaService
import com.gsmsv.api.database.service.TeamDatabaseQuotaUsage
import com.gsmsv.api.global.config.QuotaProperties
import com.gsmsv.api.global.config.ResourceQuota
import com.gsmsv.api.team.domain.Team
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TeamUsageServiceTest {
    private val vmUsageReader = mockk<TeamVmUsageReader>()
    private val databaseQuotaService = mockk<DatabaseQuotaService>()
    private val appQuotaService = mockk<AppQuotaService>()
    private val service =
        TeamUsageService(
            QuotaProperties(ResourceQuota(4, 8, 30), ResourceQuota(8, 16, 60)),
            vmUsageReader,
            databaseQuotaService,
            appQuotaService,
        )

    @Test
    fun `팀별 서비스 사용량과 한도를 하나의 응답으로 합친다`() {
        every { vmUsageReader.readUsage(TEAM_ID) } returns TeamVmUsage(4, 8, 40)
        every { databaseQuotaService.teamUsage(TEAM_ID) } returns TeamDatabaseQuotaUsage(2, 4, 1, 1, 2.1, 2.0, true)
        every { appQuotaService.teamUsage(TEAM_ID) } returns AppQuotaUsage(1, 4, 1024, 4096, 1, 2)

        val response = service.get(Team(TEAM_ID, "팀"))

        assertEquals(TEAM_ID, response.teamId)
        assertEquals(4, response.vm.vcpu.used)
        assertEquals(8, response.vm.vcpu.limit)
        assertEquals(40, response.vm.ssdGb.used)
        assertEquals(2.1, response.db.storageGb.used)
        assertEquals(true, response.db.writeLocked)
        assertEquals(1024, response.app.memoryMb.used)
        assertEquals(2, response.app.alwaysOn.limit)
        assertNull(response.updatedAt)
    }

    private companion object {
        const val TEAM_ID = "team-4p8w2k9m1xza"
    }
}
