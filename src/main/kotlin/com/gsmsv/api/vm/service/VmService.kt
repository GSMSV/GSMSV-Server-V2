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
