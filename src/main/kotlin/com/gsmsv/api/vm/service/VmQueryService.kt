package com.gsmsv.api.vm.service

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Actor
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.PermissionsResolver
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.grn.GrnParser
import com.gsmsv.api.global.response.Cursor
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.global.response.OwnerResponse
import com.gsmsv.api.network.service.DomainService
import com.gsmsv.api.team.service.TeamService
import com.gsmsv.api.user.service.UserService
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.dto.VmDetailResponse
import com.gsmsv.api.vm.dto.VmSpecResponse
import com.gsmsv.api.vm.dto.VmSummaryResponse
import com.gsmsv.api.vm.dto.VmUsageResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * VM 목록·상세 (Instance 4.5·4.6). 상세는 로더가 돌려준 [Vm]을 받고, 목록은 `listVisible`로만 꺼낸다.
 *
 * 전원·사용률은 [VmLiveStatusReader]로 덧붙인다. 그 값을 못 받아도 응답은 실패하지 않는다.
 */
@Service
class VmQueryService(
    private val loader: ServiceResourceLoader,
    private val reader: VmResourceReader,
    private val permissionsResolver: PermissionsResolver,
    private val liveStatusReader: VmLiveStatusReader,
    private val userService: UserService,
    private val teamService: TeamService,
    private val domainService: DomainService,
) {
    /** [owner]가 null이면 볼 수 있는 VM 전부, 볼 수 없는 소유자면 404다 */
    @Transactional(readOnly = true)
    fun list(
        actor: Actor,
        owner: Owner?,
        page: CursorPageRequest,
    ): CursorPage<VmSummaryResponse> {
        val cursor = page.decodedCursor()
        val rows =
            loader
                .listVisible(actor, reader, owner)
                .sortedWith(compareByDescending<Vm> { it.createdAt }.thenByDescending { it.id })
                .filter { cursor == null || it.createdAt < cursor.createdAt || (it.createdAt == cursor.createdAt && it.id < cursor.id) }
                .take(page.limit + 1)
        val ownerNames = ownerNamesOf(rows.take(page.limit).map { it.owner })
        return CursorPage.of(rows, page.limit, { Cursor(it.createdAt, it.id) }) {
            toSummary(actor, it, ownerNames[it.owner].orEmpty())
        }
    }

    @Transactional(readOnly = true)
    fun get(
        actor: Actor,
        vm: Vm,
    ): VmDetailResponse {
        val owner = vm.owner
        val users = userService.findSummariesByIds(listOfNotNull(vm.createdBy, owner.id.takeIf { owner is Owner.User }))
        val ownerName =
            when (owner) {
                is Owner.User -> users[owner.id]?.name.orEmpty()
                is Owner.Team -> teamService.findById(owner.id)?.name.orEmpty()
            }
        val live = liveStatusReader.read(vm)
        return VmDetailResponse(
            grn = GrnParser.format(owner.id, vm.id),
            id = vm.id,
            name = vm.name,
            owner = OwnerResponse.of(owner, ownerName),
            status = vm.status.name.lowercase(),
            powerState = live.powerState.name.lowercase(),
            image = vm.image,
            spec = specOf(vm),
            usage = usageOf(live),
            internalIp = vm.internalIp,
            purpose = vm.purpose,
            initialized = vm.initialized,
            // SSH 포트포워딩 기능이 아직 없어 저장된 `kind: ssh` 규칙도 없다
            ssh = null,
            // 자동 스냅샷 설정은 스냅샷 기능과 함께 저장한다. 그전까지는 꺼진 것으로 본다
            autoSnapshot = false,
            snapshots = emptyList(),
            portForwards = emptyList(),
            domains = domainService.list(vm).items,
            firewallRules = emptyList(),
            createdBy = users[vm.createdBy]?.name.orEmpty(),
            createdAt = vm.createdAt,
            updatedAt = vm.updatedAt,
            expiresAt = vm.expiresAt,
            permissions = permissionsResolver.resolve(actor, vm, DETAIL_ACTIONS),
        )
    }

    private fun toSummary(
        actor: Actor,
        vm: Vm,
        ownerName: String,
    ): VmSummaryResponse {
        val live = liveStatusReader.read(vm)
        return VmSummaryResponse(
            grn = GrnParser.format(vm.owner.id, vm.id),
            id = vm.id,
            name = vm.name,
            owner = OwnerResponse.of(vm.owner, ownerName),
            status = vm.status.name.lowercase(),
            powerState = live.powerState.name.lowercase(),
            image = vm.image,
            spec = specOf(vm),
            usage = usageOf(live),
            internalIp = vm.internalIp,
            purpose = vm.purpose,
            createdAt = vm.createdAt,
            expiresAt = vm.expiresAt,
            permissions = permissionsResolver.resolve(actor, vm, LIST_ACTIONS),
        )
    }

    /** 한 페이지의 소유자 이름을 한 번에 읽는다. 사용자는 한 번에, 팀은 서로 다른 팀마다 한 번 읽는다 */
    private fun ownerNamesOf(owners: List<Owner>): Map<Owner, String> {
        val distinct = owners.toSet()
        val users = userService.findSummariesByIds(distinct.filterIsInstance<Owner.User>().map { it.id })
        return distinct.associateWith { owner ->
            when (owner) {
                is Owner.User -> users[owner.id]?.name.orEmpty()
                is Owner.Team -> teamService.findById(owner.id)?.name.orEmpty()
            }
        }
    }

    private fun specOf(vm: Vm) = VmSpecResponse(vcpu = vm.vcpu, ramGb = vm.ramGb, ssdGb = vm.ssdGb)

    private fun usageOf(live: VmLiveStatus) =
        live.usage?.let {
            VmUsageResponse(cpuPercent = it.cpuPercent, memoryPercent = it.memoryPercent, uptimeSec = it.uptimeSec)
        }

    private companion object {
        /** 목록 화면 버튼에 필요한 동작만 (Contracts-V2 `VmListPermissions`) */
        val LIST_ACTIONS = listOf(Action.DELETE, Action.POWER, Action.EXTEND)

        /** Contracts-V2 `VmPermissions` 순서 */
        val DETAIL_ACTIONS =
            listOf(
                Action.VIEW,
                Action.VIEW_ACCESS_INFO,
                Action.UPDATE,
                Action.DELETE,
                Action.POWER,
                Action.RESIZE,
                Action.SNAPSHOT,
                Action.PORT_FORWARD,
                Action.FIREWALL,
                Action.DOMAIN,
                Action.EXTEND,
            )
    }
}
