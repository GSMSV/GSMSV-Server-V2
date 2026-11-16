package com.gsmsv.api.vm.service

import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceReader
import com.gsmsv.api.global.grn.ResourceType
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.repository.VmRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** VM 자원 조회 포트. 삭제 표시가 된 VM은 돌려주지 않는다 (일반 사용자에게는 없는 자원이다). */
@Component
class VmResourceReader(
    private val vmRepository: VmRepository,
) : ServiceResourceReader<Vm> {
    override val type: ResourceType = ResourceType.VM

    @Transactional(readOnly = true)
    override fun findById(id: String): Vm? = vmRepository.findByIdAndDeletedAtIsNull(id)

    @Transactional(readOnly = true)
    override fun findAll(): List<Vm> = vmRepository.findAllByDeletedAtIsNull()

    @Transactional(readOnly = true)
    override fun findAllByOwners(owners: Collection<Owner>): List<Vm> =
        listOf(
            Vm.OwnerType.USER to owners.filterIsInstance<Owner.User>(),
            Vm.OwnerType.TEAM to owners.filterIsInstance<Owner.Team>(),
        ).filter { (_, byType) -> byType.isNotEmpty() }
            .flatMap { (ownerType, byType) ->
                vmRepository.findAllByOwnerTypeAndOwnerIdInAndDeletedAtIsNull(ownerType, byType.map { it.id })
            }
}
