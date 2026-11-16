package com.gsmsv.api.app.service

import com.gsmsv.api.app.domain.AppOwnerType
import com.gsmsv.api.app.repository.AppRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 개인 앱 한도와 사용량. 다른 도메인은 앱 repository 대신 이 service로 받는다 */
@Service
class AppQuotaService(
    private val appRepository: AppRepository,
) {
    /**
     * 앱 개수는 삭제가 끝날 때까지 센다 (Contracts-V2 `policies/quota.md` 1장).
     *
     * **(미정)** 서비스 칸(메모리·상시 실행)은 아직 없어서 메모리 합계와 상시 실행 칸 수는 0이다.
     */
    @Transactional(readOnly = true)
    fun personalUsage(userId: String): AppQuotaUsage =
        AppQuotaUsage(
            countUsed = appRepository.countByOwnerTypeAndOwnerId(AppOwnerType.USER, userId),
            countLimit = AppQuota.PERSONAL_COUNT,
            memoryUsedMib = 0,
            memoryLimitMib = AppQuota.PERSONAL_MEMORY_MIB,
            alwaysOnUsed = 0,
            alwaysOnLimit = AppQuota.PERSONAL_ALWAYS_ON,
        )
}

data class AppQuotaUsage(
    val countUsed: Long,
    val countLimit: Long,
    val memoryUsedMib: Long,
    val memoryLimitMib: Long,
    val alwaysOnUsed: Long,
    val alwaysOnLimit: Long,
)

/** 앱 한도 (Contracts-V2 `services/one-touch-deploy.md` 8장) */
internal object AppQuota {
    const val PERSONAL_COUNT = 2L
    const val TEAM_COUNT = 4L

    // 개인 메모리 할당량 합계 2 GiB
    const val PERSONAL_MEMORY_MIB = 2048L
    const val PERSONAL_ALWAYS_ON = 1L
}
