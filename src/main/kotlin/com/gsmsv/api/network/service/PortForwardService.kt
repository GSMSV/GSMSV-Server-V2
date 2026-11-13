package com.gsmsv.api.network.service

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.SubResourceType
import com.gsmsv.api.infra.network.NetworkInfraClient
import com.gsmsv.api.infra.network.PortProtocol
import com.gsmsv.api.infra.network.VmPortRule
import com.gsmsv.api.network.config.PortForwardProperties
import com.gsmsv.api.network.domain.PortForward
import com.gsmsv.api.network.domain.PortForwardKind
import com.gsmsv.api.network.domain.PortForwardProtocol
import com.gsmsv.api.network.dto.CreatePortForwardRequest
import com.gsmsv.api.network.dto.CreatePortForwardResponse
import com.gsmsv.api.network.dto.PortForwardLimitsResponse
import com.gsmsv.api.network.dto.PortForwardListResponse
import com.gsmsv.api.network.dto.PortForwardResponse
import com.gsmsv.api.network.repository.PortForwardLock
import com.gsmsv.api.network.repository.PortForwardRepository
import com.gsmsv.api.vm.domain.Vm
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.random.Random

/** VM 생성 흐름이 넘기는 기본 포트 하나. 외부 포트 계산식은 아직 정해지지 않아 부르는 쪽이 정한다 */
data class DefaultPortForward(
    val kind: PortForwardKind,
    val externalPort: Int,
    val internalPort: Int,
)

/**
 * 포트포워딩 목록(5.6)·커스텀 추가(5.7). 로더가 돌려준 [Vm]을 받으므로 권한 판단은 끝난 상태다.
 * 다른 VM의 포트가 섞이지 않도록 그 VM의 id로만 조회한다.
 */
@Service
class PortForwardService(
    private val repository: PortForwardRepository,
    private val lock: PortForwardLock,
    private val networkInfraClient: NetworkInfraClient,
    private val properties: PortForwardProperties,
    private val random: Random = Random.Default,
) {
    @Transactional(readOnly = true)
    fun list(vm: Vm): PortForwardListResponse =
        PortForwardListResponse(
            items = repository.findAllByVmIdOrderByCreatedAtAscIdAsc(vm.id).map(::toResponse),
            publicHost = properties.publicHost,
            limits = PortForwardLimitsResponse(custom = properties.customLimit),
        )

    /**
     * 빈 외부 포트를 골라 먼저 저장(선점)하고, 그 VM의 포트 집합 전체를 Go에 보낸다.
     *
     * 저장부터 Go 반영까지 한 트랜잭션이다. 반영이 실패하면 예외가 트랜잭션을 되돌려 저장한 기록이 남지 않는다.
     * 다른 요청이 같은 외부 포트를 먼저 저장했으면 유니크 제약에 걸려 409 `CONFLICT`다 (다시 시도하면 된다).
     */
    @Transactional
    fun create(
        vm: Vm,
        request: CreatePortForwardRequest,
    ): CreatePortForwardResponse {
        val internalIp = vm.internalIp ?: throw BusinessException(ErrorCode.VM_NO_INTERNAL_IP)
        lock.lock(vm.id)
        val used = repository.countByVmIdAndKind(vm.id, PortForwardKind.CUSTOM).toInt()
        if (used >= properties.customLimit) {
            throw BusinessException(ErrorCode.QUOTA_EXCEEDED, details = mapOf("limit" to properties.customLimit, "used" to used))
        }
        val portForward =
            PortForward.createCustom(
                id = IdGenerator.subResourceId(SubResourceType.PORT_FORWARD),
                vmId = vm.id,
                externalPort = pickFreeExternalPort(),
                internalPort = requireNotNull(request.internalPort),
                protocol = PortForwardProtocol.valueOf(requireNotNull(request.protocol).uppercase()),
                allowedSource = request.allowedSource,
                description = request.description,
            )
        try {
            repository.saveAndFlush(portForward)
        } catch (_: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.CONFLICT)
        }
        syncPorts(vm.id, internalIp)
        return CreatePortForwardResponse(
            id = portForward.id,
            externalPort = portForward.externalPort,
            internalPort = portForward.internalPort,
            protocol = portForward.protocol.name.lowercase(),
            syncStatus = SYNC_STATUS_SYNCED,
        )
    }

    /**
     * VM 생성 흐름이 기본 포트 3종을 저장할 때 부른다. Go 반영은 VM 생성 흐름이 내부 IP를 받은 뒤 포트 집합으로 함께 한다.
     * 트랜잭션은 부르는 쪽 것을 따른다.
     */
    @Transactional
    fun saveDefaults(
        vm: Vm,
        defaults: List<DefaultPortForward>,
    ): List<PortForward> {
        require(defaults.map { it.kind }.toSet().size == defaults.size) { "같은 종류의 기본 포트가 겹칩니다." }
        lock.lock(vm.id)
        return repository.saveAllAndFlush(
            defaults.map {
                PortForward.createDefault(
                    id = IdGenerator.subResourceId(SubResourceType.PORT_FORWARD),
                    vmId = vm.id,
                    kind = it.kind,
                    externalPort = it.externalPort,
                    internalPort = it.internalPort,
                )
            },
        )
    }

    // 커스텀 범위에서 무작위로 골라 비었는지 본다. 이미 쓰는 번호를 모두 읽지 않아 범위가 넓어도 조회가 짧다
    private fun pickFreeExternalPort(): Int {
        val range = properties.customPortRange
        repeat(MAX_PICK_ATTEMPTS) {
            val candidate = random.nextInt(range.first, range.last + 1)
            if (!repository.existsByExternalPort(candidate)) return candidate
        }
        throw BusinessException(ErrorCode.NO_FREE_PORT)
    }

    private fun syncPorts(
        vmId: String,
        internalIp: String,
    ) {
        val rules =
            repository.findAllByVmIdOrderByCreatedAtAscIdAsc(vmId).map {
                VmPortRule(
                    id = it.id,
                    external = it.externalPort,
                    internal = it.internalPort,
                    protocol =
                        when (it.protocol) {
                            PortForwardProtocol.TCP -> PortProtocol.TCP
                            PortForwardProtocol.UDP -> PortProtocol.UDP
                        },
                    allowedSource = it.allowedSource,
                )
            }
        networkInfraClient.syncVmPorts(vmId, internalIp, rules)
    }

    private fun toResponse(portForward: PortForward) =
        PortForwardResponse(
            id = portForward.id,
            kind = portForward.kind.name.lowercase(),
            externalPort = portForward.externalPort,
            internalPort = portForward.internalPort,
            protocol = portForward.protocol.name.lowercase(),
            allowedSource = portForward.allowedSource,
            description = portForward.description,
        )

    private companion object {
        const val MAX_PICK_ATTEMPTS = 100

        // 동기 처리라 성공하면 언제나 반영된 상태다. 값 이름은 계약 SyncStatus(pending·synced·failed)를 따른다
        const val SYNC_STATUS_SYNCED = "synced"
    }
}
