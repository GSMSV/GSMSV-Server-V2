package com.gsmsv.api.quota.service

import com.gsmsv.api.app.service.AppQuotaService
import com.gsmsv.api.app.service.AppQuotaUsage
import com.gsmsv.api.database.service.DatabaseQuotaService
import com.gsmsv.api.database.service.DatabaseQuotaUsage
import com.gsmsv.api.global.config.QuotaProperties
import com.gsmsv.api.global.config.ResourceQuota
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.quota.dto.MaxCreatableResponse
import com.gsmsv.api.quota.dto.VmQuotaUsageResponse
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MyQuotaServiceTest {
    private val vmUsageReader = mockk<PersonalVmUsageReader>()
    private val databaseQuotaService = mockk<DatabaseQuotaService>()
    private val appQuotaService = mockk<AppQuotaService>()
    private val service =
        MyQuotaService(
            QuotaProperties(personal = ResourceQuota(4, 8, 30), team = ResourceQuota(8, 16, 60)),
            vmUsageReader,
            databaseQuotaService,
            appQuotaService,
        )

    @BeforeEach
    fun setUp() {
        every { databaseQuotaService.personalUsage(ME) } returns DatabaseQuotaUsage(1, 2, 0.12, 1.0)
        every { appQuotaService.personalUsage(ME) } returns AppQuotaUsage(1, 2, 0, 2048, 0, 1)
    }

    @Test
    fun `일반 사용자는 1인 한도와 남은 한도만큼의 최대 사양을 받는다`() {
        every { vmUsageReader.allocated(ME) } returns VmAllocation(2, 4, 20)

        val result = service.get(AuthenticatedUser(ME, Role.USER))

        assertThat(result.vm.vcpu).isEqualTo(VmQuotaUsageResponse(2, 4))
        assertThat(result.vm.ramGb).isEqualTo(VmQuotaUsageResponse(4, 8))
        assertThat(result.vm.ssdGb).isEqualTo(VmQuotaUsageResponse(20, 30))
        assertThat(result.vm.maxCreatable).isEqualTo(MaxCreatableResponse(2, 4, 10))
    }

    @Test
    fun `사용량이 한도보다 크면 최대 사양은 0이다`() {
        every { vmUsageReader.allocated(ME) } returns VmAllocation(6, 8, 40)

        val result = service.get(AuthenticatedUser(ME, Role.USER))

        assertThat(result.vm.maxCreatable).isEqualTo(MaxCreatableResponse(0, 0, 0))
    }

    @Test
    fun `관리자는 VM 한도와 최대 사양이 null이고 서비스 한도는 같다`() {
        every { vmUsageReader.allocated(ME) } returns VmAllocation(6, 12, 80)

        val result = service.get(AuthenticatedUser(ME, Role.ADMIN))

        assertThat(result.vm.vcpu).isEqualTo(VmQuotaUsageResponse(6, null))
        assertThat(result.vm.ramGb.limit).isNull()
        assertThat(result.vm.ssdGb.limit).isNull()
        assertThat(result.vm.maxCreatable).isEqualTo(MaxCreatableResponse(null, null, null))
        assertThat(result.db.count.limit).isEqualTo(2)
        assertThat(result.fn.count.limit).isEqualTo(3)
        assertThat(result.app.memoryMb.limit).isEqualTo(2048)
    }

    @Test
    fun `DB와 앱 사용량은 각 도메인 service 값을 그대로 쓴다`() {
        every { vmUsageReader.allocated(ME) } returns VmAllocation(0, 0, 0)

        val result = service.get(AuthenticatedUser(ME, Role.USER))

        assertThat(result.db.count.used).isEqualTo(1)
        assertThat(result.db.sharedStorageGb.used).isEqualTo(0.12)
        assertThat(result.db.sharedStorageGb.limit).isEqualTo(1.0)
        assertThat(result.fn.dailyExecutions.limit).isEqualTo(10_000)
        assertThat(result.app.count.used).isEqualTo(1)
        assertThat(result.app.alwaysOn.limit).isEqualTo(1)
    }

    private companion object {
        const val ME = "user-a1b2c3d4e5f6"
    }
}
