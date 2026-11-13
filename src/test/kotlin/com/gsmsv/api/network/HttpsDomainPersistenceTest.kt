package com.gsmsv.api.network

import com.gsmsv.api.TestcontainersConfiguration
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.ResourceType
import com.gsmsv.api.global.grn.SubResourceType
import com.gsmsv.api.network.domain.HttpsDomain
import com.gsmsv.api.network.repository.HttpsDomainRepository
import com.gsmsv.api.network.repository.HttpsRouteLock
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.repository.UserRepository
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.repository.VmRepository
import com.gsmsv.api.vm.service.VmService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="])
@Tag("integration")
@Transactional
class HttpsDomainPersistenceTest
    @Autowired
    constructor(
        private val domainRepository: HttpsDomainRepository,
        private val routeLock: HttpsRouteLock,
        private val vmRepository: VmRepository,
        private val vmService: VmService,
        private val userRepository: UserRepository,
    ) {
        @Test
        fun `서브도메인은 다른 VM이어도 전체에서 하나만 저장된다`() {
            val creatorId = saveUser()
            val first = saveVm(creatorId)
            val second = saveVm(creatorId)
            domainRepository.saveAndFlush(domain(first.id, "my-app"))

            assertFailsWith<DataIntegrityViolationException> {
                domainRepository.saveAndFlush(domain(second.id, "my-app"))
            }
        }

        @Test
        fun `VM별 목록과 개수는 그 VM의 도메인만 센다`() {
            val creatorId = saveUser()
            val vm = saveVm(creatorId)
            val other = saveVm(creatorId)
            domainRepository.saveAndFlush(domain(vm.id, "second", Instant.parse("2026-11-02T00:00:00Z")))
            domainRepository.saveAndFlush(domain(vm.id, "first", Instant.parse("2026-11-01T00:00:00Z")))
            domainRepository.saveAndFlush(domain(other.id, "third"))

            assertEquals(2, domainRepository.countByVmId(vm.id))
            assertEquals(listOf("first", "second"), domainRepository.findAllByVmIdOrderByCreatedAtAscIdAsc(vm.id).map { it.subdomain })
        }

        @Test
        fun `라우트 잠금은 같은 트랜잭션에서 다시 잡아도 막히지 않는다`() {
            routeLock.lock()
            routeLock.lock()
        }

        @Test
        fun `내부 IP는 지워지지 않고 IP가 있는 VM만 돌려준다`() {
            val creatorId = saveUser()
            val active = saveVm(creatorId).also { it.markActive("192.0.2.10") }
            val creating = saveVm(creatorId)
            val deleted =
                saveVm(creatorId).also {
                    it.markActive("192.0.2.11")
                    it.startDeleting()
                    it.markDeleted()
                }
            vmRepository.saveAllAndFlush(listOf(active, deleted))

            assertEquals(mapOf(active.id to "192.0.2.10"), vmService.internalIpsOf(listOf(active.id, creating.id, deleted.id)))
        }

        private fun domain(
            vmId: String,
            subdomain: String,
            createdAt: Instant = Instant.now(),
        ) = HttpsDomain(IdGenerator.subResourceId(SubResourceType.DOMAIN), vmId, subdomain, 8080, createdAt)

        private fun saveUser(): String {
            val id = IdGenerator.userId()
            userRepository.saveAndFlush(
                User(id = id, dataGsmId = "gsm-$id", email = "$id@example.com", name = "테스트 사용자"),
            )
            return id
        }

        private fun saveVm(creatorId: String): Vm =
            vmRepository.saveAndFlush(
                Vm.create(
                    id = IdGenerator.resourceId(ResourceType.VM),
                    name = "테스트 VM",
                    purpose = null,
                    owner = Owner.User(creatorId),
                    image = "ubuntu-24.04",
                    vcpu = 2,
                    ramGb = 4,
                    ssdGb = 20,
                    initialUsername = "ubuntu",
                    initialPasswordEncrypted = "encrypted",
                    createdBy = creatorId,
                    expiresAt = Instant.now().plusSeconds(3600),
                ),
            )
    }
