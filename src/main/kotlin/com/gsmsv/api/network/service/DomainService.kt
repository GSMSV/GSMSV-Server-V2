package com.gsmsv.api.network.service

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.SubResourceType
import com.gsmsv.api.global.response.FieldErrorDetail
import com.gsmsv.api.infra.network.HttpsRoute
import com.gsmsv.api.infra.network.NetworkInfraClient
import com.gsmsv.api.network.config.HttpsDomainProperties
import com.gsmsv.api.network.domain.HttpsDomain
import com.gsmsv.api.network.dto.CreateDomainResponse
import com.gsmsv.api.network.dto.DomainLimits
import com.gsmsv.api.network.dto.DomainListResponse
import com.gsmsv.api.network.dto.DomainResponse
import com.gsmsv.api.network.repository.HttpsDomainRepository
import com.gsmsv.api.network.repository.HttpsRouteLock
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.service.VmService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * HTTPS 도메인 목록·연결 (Network 5.10·5.11). 로더가 돌려준 [Vm]을 받으므로 권한 판단은 끝난 상태다.
 *
 * DB가 진실이다 (23번 9장). 연결할 때마다 DB의 활성 도메인 전체로 라우트 목록을 만들어 Go에 통째로 보낸다.
 */
@Service
class DomainService(
    private val domainRepository: HttpsDomainRepository,
    private val routeLock: HttpsRouteLock,
    private val vmService: VmService,
    private val networkInfraClient: NetworkInfraClient,
    private val properties: HttpsDomainProperties,
) {
    @Transactional(readOnly = true)
    fun list(vm: Vm): DomainListResponse =
        DomainListResponse(
            items = domainRepository.findAllByVmIdOrderByCreatedAtAscIdAsc(vm.id).map(::toResponse),
            limits = DomainLimits(perVm = properties.perVmLimit),
        )

    /**
     * 레코드를 먼저 저장해 서브도메인을 선점(유니크 제약)한 뒤 라우트 전체를 Go에 반영한다.
     *
     * 반영에 실패하면 예외가 트랜잭션을 되돌려 저장한 레코드도 사라진다 (명세 502 `GATEWAY_SYNC_FAILED`: 저장한 기록은 지운다).
     *
     * @throws BusinessException 예약어는 400 `VALIDATION_FAILED`, 내부 IP가 없으면 409 `VM_NO_INTERNAL_IP`,
     *   VM당 한도를 넘으면 422 `QUOTA_EXCEEDED`, 이미 쓰는 서브도메인이면 409 `ALREADY_EXISTS`, 반영 실패는 502·503
     */
    @Transactional
    fun create(
        vm: Vm,
        subdomain: String,
        internalPort: Int,
    ): CreateDomainResponse {
        if (subdomain in properties.reservedSubdomains) {
            throw BusinessException(
                ErrorCode.VALIDATION_FAILED,
                details = mapOf("fields" to listOf(FieldErrorDetail("subdomain", "예약어는 서브도메인으로 쓸 수 없습니다."))),
            )
        }
        if (vm.internalIp == null) throw BusinessException(ErrorCode.VM_NO_INTERNAL_IP)

        routeLock.lock()
        val used = domainRepository.countByVmId(vm.id)
        if (used >= properties.perVmLimit) {
            throw BusinessException(
                ErrorCode.QUOTA_EXCEEDED,
                details = mapOf("limit" to properties.perVmLimit, "used" to used.toInt()),
            )
        }
        if (domainRepository.existsBySubdomain(subdomain)) throw BusinessException(ErrorCode.ALREADY_EXISTS)

        val domain =
            try {
                domainRepository.saveAndFlush(
                    HttpsDomain(IdGenerator.subResourceId(SubResourceType.DOMAIN), vm.id, subdomain, internalPort),
                )
            } catch (_: DataIntegrityViolationException) {
                // 잠금 밖의 경로(예: 수정)가 같은 이름을 먼저 잡은 경우
                throw BusinessException(ErrorCode.ALREADY_EXISTS)
            }

        syncAllRoutes()
        return CreateDomainResponse(id = domain.id, url = urlOf(domain.subdomain), syncStatus = domain.syncStatus.wire)
    }

    /** DB의 활성 도메인 전체로 라우트를 교체하고, 반영된 도메인을 `synced`로 바꾼다 */
    private fun syncAllRoutes() {
        val domains = domainRepository.findAllByOrderByCreatedAtAscIdAsc()
        val internalIps = vmService.internalIpsOf(domains.map { it.vmId }.toSet())
        // 지워졌거나 내부 IP가 없는 VM의 도메인은 라우트에서 뺀다
        val active = domains.filter { it.vmId in internalIps }
        val routes =
            active.map {
                HttpsRoute(id = it.id, host = hostOf(it.subdomain), upstream = "${internalIps.getValue(it.vmId)}:${it.internalPort}")
            }
        networkInfraClient.syncHttpsRoutes(routes)
        active.forEach { it.markSynced() }
    }

    private fun toResponse(domain: HttpsDomain) =
        DomainResponse(
            id = domain.id,
            subdomain = domain.subdomain,
            url = urlOf(domain.subdomain),
            internalPort = domain.internalPort,
            syncStatus = domain.syncStatus.wire,
        )

    private fun hostOf(subdomain: String) = "$subdomain.${properties.baseDomain}"

    private fun urlOf(subdomain: String) = "https://${hostOf(subdomain)}"
}
