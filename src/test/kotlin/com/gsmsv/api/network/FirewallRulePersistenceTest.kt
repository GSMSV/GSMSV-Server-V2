package com.gsmsv.api.network

import com.gsmsv.api.TestcontainersConfiguration
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.ResourceType
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.infra.firewall.FakeFirewallInfraClient
import com.gsmsv.api.network.dto.CreateFirewallRuleRequest
import com.gsmsv.api.network.repository.FirewallRuleRepository
import com.gsmsv.api.network.repository.VmFirewallRepository
import com.gsmsv.api.network.service.FirewallRuleResourceReader
import com.gsmsv.api.network.service.FirewallRuleService
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.repository.UserRepository
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.repository.VmRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// 서비스가 트랜잭션을 나눠 쓰므로 테스트 트랜잭션으로 감싸지 않고, 끝나면 직접 지운다
@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="])
@Tag("integration")
class FirewallRulePersistenceTest
    @Autowired
    constructor(
        private val service: FirewallRuleService,
        private val ruleReader: FirewallRuleResourceReader,
        private val ruleRepository: FirewallRuleRepository,
        private val firewallRepository: VmFirewallRepository,
        private val vmRepository: VmRepository,
        private val userRepository: UserRepository,
        private val infra: FakeFirewallInfraClient,
    ) {
        @AfterEach
        fun tearDown() {
            ruleRepository.deleteAll()
            firewallRepository.deleteAll()
            infra.reset()
        }

        @Test
        fun `추가한 규칙을 목록에서 만든 순서로 읽고 지워도 방화벽은 켜져 있다`() {
            val vm = saveActiveVm()

            val first = service.create(vm, CreateFirewallRuleRequest(dport = "22", source = "203.0.113.0/24"))
            val second = service.create(vm, CreateFirewallRuleRequest(action = "drop", protocol = "icmp"))
            val listed = service.list(vm, CursorPageRequest())

            assertTrue(listed.enabled)
            assertEquals(listOf(first.id, second.id), listed.items.map { it.id })
            assertEquals("drop", listed.items[1].action)
            assertEquals(vm.id, assertNotNull(ruleReader.findById(first.id)).parentId)

            service.delete(vm, assertNotNull(ruleReader.findById(first.id)))
            service.delete(vm, assertNotNull(ruleReader.findById(second.id)))

            val afterDelete = service.list(vm, CursorPageRequest())
            assertTrue(afterDelete.enabled)
            assertTrue(afterDelete.items.isEmpty())
            assertTrue(infra.isEnabled(vm.id))
            assertTrue(infra.currentRules(vm.id).isEmpty())
        }

        @Test
        fun `Go 반영에 실패하면 규칙과 처음 만든 방화벽 행을 남기지 않는다`() {
            val vm = saveActiveVm()
            infra.failWith(BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE))

            assertFailsWith<BusinessException> { service.create(vm, CreateFirewallRuleRequest()) }

            assertEquals(0, ruleRepository.countByVmId(vm.id))
            assertFalse(firewallRepository.existsById(vm.id))
        }

        private fun saveActiveVm(): Vm {
            val userId = IdGenerator.userId()
            userRepository.saveAndFlush(
                User(id = userId, dataGsmId = "gsm-$userId", email = "$userId@example.com", name = "테스트 사용자"),
            )
            val vm =
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
                )
            vm.markActive("192.0.2.10")
            return vmRepository.saveAndFlush(vm)
        }
    }
