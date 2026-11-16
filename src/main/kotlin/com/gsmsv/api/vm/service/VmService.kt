package com.gsmsv.api.vm.service

import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.domain.VmStatus
import com.gsmsv.api.vm.repository.VmRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 소유자가 쓰고 있는 VM 사양 합계 */
data class VmUsage(
    val vcpu: Int,
    val ramGib: Int,
    val ssdGib: Int,
)

@Service
class VmService(
    private val vmRepository: VmRepository,
) {
    /** 개인 VM 사용량 합계. 내 한도(4.2)가 읽는다 */
    @Transactional(readOnly = true)
    fun personalUsage(userId: String): VmUsage = usageOf(Vm.OwnerType.USER, userId)

    @Transactional(readOnly = true)
    fun teamUsage(teamId: String): VmUsage = usageOf(Vm.OwnerType.TEAM, teamId)

    /**
     * VM id → 내부 IP. 활성 도메인 전체로 HTTPS 라우트를 만들 때처럼 DB 전체로 선언형 상태를 만드는 데만 쓴다.
     * 권한 판단을 거친 자원 객체가 아니므로 사용자 응답에 쓰지 않는다. 지워졌거나 내부 IP가 없는 VM은 빠진다.
     */
    @Transactional(readOnly = true)
    fun internalIpsOf(vmIds: Collection<String>): Map<String, String> =
        if (vmIds.isEmpty()) {
            emptyMap()
        } else {
            vmRepository.findInternalIps(vmIds).associate { it.vmId to it.internalIp }
        }

    private fun usageOf(
        ownerType: Vm.OwnerType,
        ownerId: String,
    ): VmUsage {
        val sum = vmRepository.sumSpecByOwner(ownerType, ownerId, QUOTA_STATUSES)
        return VmUsage(vcpu = sum.vcpu.toInt(), ramGib = sum.ramGb.toInt(), ssdGib = sum.ssdGb.toInt())
    }

    private companion object {
        /** 자원을 잡고 있는 상태만 센다. 실패하거나 지워진 VM은 한도를 쓰지 않는다 */
        val QUOTA_STATUSES = setOf(VmStatus.CREATING, VmStatus.ACTIVE, VmStatus.SUSPENDED, VmStatus.DELETING)
    }
}
