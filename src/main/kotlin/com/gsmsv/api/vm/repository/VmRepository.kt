package com.gsmsv.api.vm.repository

import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.domain.VmStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** id 조회는 [com.gsmsv.api.vm.service.VmResourceReader] 밖에서 부르지 않는다 (22번 규칙). */
interface VmRepository : JpaRepository<Vm, String> {
    fun findByIdAndDeletedAtIsNull(id: String): Vm?

    fun findAllByDeletedAtIsNull(): List<Vm>

    fun findAllByOwnerTypeAndOwnerIdInAndDeletedAtIsNull(
        ownerType: Vm.OwnerType,
        ownerIds: Collection<String>,
    ): List<Vm>

    /** 소유자가 쓰는 사양 합계. 목록이 아니라 합계만 읽는다 */
    @Query(
        """
        select new com.gsmsv.api.vm.repository.VmSpecSum(
            coalesce(sum(v.vcpu), 0), coalesce(sum(v.ramGb), 0), coalesce(sum(v.ssdGb), 0))
        from Vm v
        where v.ownerType = :ownerType and v.ownerId = :ownerId and v.status in :statuses
        """,
    )
    fun sumSpecByOwner(
        @Param("ownerType") ownerType: Vm.OwnerType,
        @Param("ownerId") ownerId: String,
        @Param("statuses") statuses: Collection<VmStatus>,
    ): VmSpecSum

    /** 선언형 네트워크 상태를 만들 때 쓴다. 자원 객체가 아니라 id와 내부 IP만 읽는다 */
    @Query(
        """
        select new com.gsmsv.api.vm.repository.VmInternalIp(v.id, v.internalIp)
        from Vm v
        where v.id in :ids and v.deletedAt is null and v.internalIp is not null
        """,
    )
    fun findInternalIps(
        @Param("ids") ids: Collection<String>,
    ): List<VmInternalIp>
}

data class VmInternalIp(
    val vmId: String,
    val internalIp: String,
)

data class VmSpecSum(
    val vcpu: Long,
    val ramGb: Long,
    val ssdGb: Long,
)
