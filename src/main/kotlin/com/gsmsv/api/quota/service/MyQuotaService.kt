package com.gsmsv.api.quota.service

import com.gsmsv.api.app.service.AppQuotaService
import com.gsmsv.api.database.service.DatabaseQuotaService
import com.gsmsv.api.global.config.QuotaProperties
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.quota.dto.CountUsageResponse
import com.gsmsv.api.quota.dto.MaxCreatableResponse
import com.gsmsv.api.quota.dto.MyAppQuotaResponse
import com.gsmsv.api.quota.dto.MyDbQuotaResponse
import com.gsmsv.api.quota.dto.MyFnQuotaResponse
import com.gsmsv.api.quota.dto.MyQuotaResponse
import com.gsmsv.api.quota.dto.MyVmQuotaResponse
import com.gsmsv.api.quota.dto.StorageUsageResponse
import com.gsmsv.api.quota.dto.VmQuotaUsageResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 내 한도와 사용량 (4.2). 본인 정보라 자원 로더를 거치지 않고 요청자 id로만 센다.
 * 사용량은 각 도메인 service에서 받는다.
 */
@Service
class MyQuotaService(
    private val quotaProperties: QuotaProperties,
    private val vmUsageReader: PersonalVmUsageReader,
    private val databaseQuotaService: DatabaseQuotaService,
    private val appQuotaService: AppQuotaService,
) {
    @Transactional(readOnly = true)
    fun get(user: AuthenticatedUser): MyQuotaResponse {
        val database = databaseQuotaService.personalUsage(user.userId)
        val app = appQuotaService.personalUsage(user.userId)
        return MyQuotaResponse(
            vm = vm(user),
            db =
                MyDbQuotaResponse(
                    count = CountUsageResponse(database.countUsed, database.countLimit),
                    sharedStorageGb = StorageUsageResponse(database.sharedStorageUsedGib, database.sharedStorageLimitGib),
                ),
            // (미정) 서버리스 도메인이 아직 없어서 사용량은 0이다
            fn =
                MyFnQuotaResponse(
                    count = CountUsageResponse(0, FN_PERSONAL_COUNT),
                    dailyExecutions = CountUsageResponse(0, FN_PERSONAL_DAILY_EXECUTIONS),
                ),
            app =
                MyAppQuotaResponse(
                    count = CountUsageResponse(app.countUsed, app.countLimit),
                    memoryMb = CountUsageResponse(app.memoryUsedMib, app.memoryLimitMib),
                    alwaysOn = CountUsageResponse(app.alwaysOnUsed, app.alwaysOnLimit),
                ),
        )
    }

    private fun vm(user: AuthenticatedUser): MyVmQuotaResponse {
        val used = vmUsageReader.allocated(user.userId)
        // 관리자 개인 VM에는 1인 한도가 없다 (Contracts-V2 policies/quota.md 3장)
        val limit = quotaProperties.personal.takeUnless { user.role == Role.ADMIN }
        return MyVmQuotaResponse(
            vcpu = VmQuotaUsageResponse(used.vcpu, limit?.vcpu),
            ramGb = VmQuotaUsageResponse(used.ramGib, limit?.ramGib),
            ssdGb = VmQuotaUsageResponse(used.ssdGib, limit?.ssdGib),
            // (미정) 노드 총량·여유(Go 13.8)를 받기 전이라 남은 1인 한도만으로 정한다. 일반 몫·노드 여유는 보지 않는다 (policies/quota.md 7장)
            maxCreatable =
                MaxCreatableResponse(
                    vcpu = limit?.let { remaining(it.vcpu, used.vcpu) },
                    ramGb = limit?.let { remaining(it.ramGib, used.ramGib) },
                    ssdGb = limit?.let { remaining(it.ssdGib, used.ssdGib) },
                ),
        )
    }

    // 한도를 낮추면 사용량이 한도보다 클 수 있다
    private fun remaining(
        limit: Int,
        used: Int,
    ): Int = (limit - used).coerceAtLeast(0)

    private companion object {
        // Contracts-V2 services/serverless.md 8장. 서버리스 도메인이 생기면 그 service에서 받는다
        const val FN_PERSONAL_COUNT = 3L
        const val FN_PERSONAL_DAILY_EXECUTIONS = 10_000L
    }
}
