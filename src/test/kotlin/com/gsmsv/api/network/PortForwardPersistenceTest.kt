package com.gsmsv.api.network

import com.gsmsv.api.TestcontainersConfiguration
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.ResourceType
import com.gsmsv.api.global.grn.SubResourceType
import com.gsmsv.api.network.domain.PortForward
import com.gsmsv.api.network.domain.PortForwardKind
import com.gsmsv.api.network.domain.PortForwardProtocol
import com.gsmsv.api.network.repository.PortForwardRepository
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.repository.UserRepository
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.repository.VmRepository
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="])
@Tag("integration")
@Transactional
class PortForwardPersistenceTest
    @Autowired
    constructor(
        private val portForwardRepository: PortForwardRepository,
        private val vmRepository: VmRepository,
        private val userRepository: UserRepository,
    ) {
        @Test
        fun `VM별 포트만 꺼내고 커스텀 개수를 센다`() {
            val vm = saveVm()
            val other = saveVm()
            portForwardRepository.saveAndFlush(PortForward.createDefault(portId(), vm.id, PortForwardKind.SSH, 20022, 22))
            portForwardRepository.saveAndFlush(
                PortForward.createCustom(portId(), vm.id, 40001, 8080, PortForwardProtocol.UDP, "203.0.113.0/24", "개발용"),
            )
            portForwardRepository.saveAndFlush(
                PortForward.createCustom(portId(), other.id, 40002, 8080, PortForwardProtocol.TCP, null, null),
            )

            val ports = portForwardRepository.findAllByVmIdOrderByCreatedAtAscIdAsc(vm.id)

            assertEquals(setOf(PortForwardKind.SSH, PortForwardKind.CUSTOM), ports.map { it.kind }.toSet())
            assertTrue(ports.all { it.vmId == vm.id })
            assertEquals(1, portForwardRepository.countByVmIdAndKind(vm.id, PortForwardKind.CUSTOM))
            assertTrue(portForwardRepository.existsByExternalPort(40002))
        }

        @Test
        fun `외부 포트는 모든 VM에서 겹칠 수 없다`() {
            val vm = saveVm()
            val other = saveVm()
            portForwardRepository.saveAndFlush(PortForward.createCustom(portId(), vm.id, 40003, 8080, PortForwardProtocol.TCP, null, null))

            assertThrows<DataIntegrityViolationException> {
                portForwardRepository.saveAndFlush(
                    PortForward.createCustom(portId(), other.id, 40003, 9090, PortForwardProtocol.UDP, null, null),
                )
            }
        }

        private fun portId() = IdGenerator.subResourceId(SubResourceType.PORT_FORWARD)

        private fun saveVm(): Vm {
            val userId = IdGenerator.userId()
            userRepository.saveAndFlush(User(id = userId, dataGsmId = "gsm-$userId", email = "$userId@example.com", name = "테스트 사용자"))
            return vmRepository.saveAndFlush(
                Vm.create(
                    id = IdGenerator.resourceId(ResourceType.VM),
                    name = "테스트 VM",
                    purpose = null,
                    owner = Owner.User(userId),
                    image = "ubuntu-24.04",
                    vcpu = 2,
                    ramGb = 4,
                    ssdGb = 20,
                    initialUsername = "ubuntu",
                    initialPasswordEncrypted = "encrypted",
                    createdBy = userId,
                    expiresAt = Instant.now().plusSeconds(3600),
                ),
            )
        }
    }
