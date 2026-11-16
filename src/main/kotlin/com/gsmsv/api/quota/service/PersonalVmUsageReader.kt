package com.gsmsv.api.quota.service

import org.springframework.stereotype.Component

/**
 * 개인 VM 할당량 합계 조회 포트.
 *
 * **(미정)** VM 도메인(#90)이 아직 없어서 [PlaceholderPersonalVmUsageReader]가 0을 돌려준다.
 * VM service가 생기면 그 service로 바꾼다 (다른 도메인 repository를 직접 부르지 않는다).
 */
interface PersonalVmUsageReader {
    fun allocated(userId: String): VmAllocation
}

/** VM 사양 합계. RAM·SSD는 GiB다 */
data class VmAllocation(
    val vcpu: Int,
    val ramGib: Int,
    val ssdGib: Int,
)

@Component
class PlaceholderPersonalVmUsageReader : PersonalVmUsageReader {
    override fun allocated(userId: String): VmAllocation = VmAllocation(0, 0, 0)
}
