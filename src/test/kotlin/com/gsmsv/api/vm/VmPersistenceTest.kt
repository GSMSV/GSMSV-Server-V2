package com.gsmsv.api.vm

import com.gsmsv.api.TestcontainersConfiguration
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.ResourceType
import com.gsmsv.api.team.service.TeamVmUsage
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.repository.UserRepository
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.repository.VmRepository
import com.gsmsv.api.vm.service.VmResourceReader
import com.gsmsv.api.vm.service.VmService
import com.gsmsv.api.vm.service.VmTeamUsageReader
import com.gsmsv.api.vm.service.VmUsage
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="])
@Tag("integration")
@Transactional
class VmPersistenceTest
    @Autowired
    constructor(
        private val vmRepository: VmRepository,
        private val vmReader: VmResourceReader,
        private val vmService: VmService,
        private val teamUsageReader: VmTeamUsageReader,
        private val userRepository: UserRepository,
    ) {
        @Test
        fun `저장한 VM을 id로 다시 꺼내고 소유자를 그대로 돌려준다`() {
            val creatorId = saveUser()
            val vm = saveVm(Owner.User(creatorId), creatorId)

            val found = vmReader.findById(vm.id)

            assertNotNull(found)
            assertEquals(Owner.User(creatorId), found.owner)
            assertEquals(ResourceType.VM, vmReader.type)
        }

        @Test
        fun `삭제 표시가 된 VM은 조회와 목록에서 빠진다`() {
            val creatorId = saveUser()
            val vm = saveVm(Owner.User(creatorId), creatorId)
            vm.markActive("192.0.2.5")
            vm.startDeleting()
            vm.markDeleted()
            vmRepository.saveAndFlush(vm)

            assertNull(vmReader.findById(vm.id))
            assertTrue(vmReader.findAllByOwners(listOf(Owner.User(creatorId))).isEmpty())
            assertNotNull(vmRepository.findById(vm.id).orElse(null))
        }

        @Test
        fun `소유자 목록으로 개인 VM과 팀 VM을 함께 거른다`() {
            val creatorId = saveUser()
            val teamId = IdGenerator.teamId()
            val mine = saveVm(Owner.User(creatorId), creatorId)
            val teams = saveVm(Owner.Team(teamId), creatorId)
            saveVm(Owner.Team(IdGenerator.teamId()), creatorId)

            val ids = vmReader.findAllByOwners(listOf(Owner.User(creatorId), Owner.Team(teamId))).map { it.id }

            assertEquals(setOf(mine.id, teams.id), ids.toSet())
        }

        @Test
        fun `팀 VM 사용량은 자원을 잡고 있는 VM의 사양 합계이고 실패·삭제된 VM은 세지 않는다`() {
            val creatorId = saveUser()
            val teamId = IdGenerator.teamId()
            saveVm(Owner.Team(teamId), creatorId, vcpu = 2, ramGb = 4, ssdGb = 20)
            saveVm(Owner.Team(teamId), creatorId, vcpu = 1, ramGb = 2, ssdGb = 10)
            saveVm(Owner.Team(teamId), creatorId, vcpu = 8, ramGb = 8, ssdGb = 80).also {
                it.markFailed()
                vmRepository.saveAndFlush(it)
            }
            saveVm(Owner.User(creatorId), creatorId, vcpu = 4, ramGb = 8, ssdGb = 30)

            assertEquals(TeamVmUsage(vcpu = 3, ramGib = 6, ssdGib = 30), teamUsageReader.readUsage(teamId))
            assertEquals(VmUsage(vcpu = 4, ramGib = 8, ssdGib = 30), vmService.personalUsage(creatorId))
        }

        @Test
        fun `VM이 없는 소유자의 사용량은 0이다`() {
            assertEquals(TeamVmUsage(0, 0, 0), teamUsageReader.readUsage(IdGenerator.teamId()))
        }

        private fun saveUser(): String {
            val id = IdGenerator.userId()
            userRepository.saveAndFlush(
                User(id = id, dataGsmId = "gsm-$id", email = "$id@example.com", name = "테스트 사용자"),
            )
            return id
        }

        private fun saveVm(
            owner: Owner,
            createdBy: String,
            vcpu: Int = 2,
            ramGb: Int = 4,
            ssdGb: Int = 20,
        ): Vm =
            vmRepository.saveAndFlush(
                Vm.create(
                    id = IdGenerator.resourceId(ResourceType.VM),
                    name = "테스트 VM",
                    purpose = null,
                    owner = owner,
                    image = "ubuntu-24.04",
                    vcpu = vcpu,
                    ramGb = ramGb,
                    ssdGb = ssdGb,
                    initialUsername = "ubuntu",
                    initialPasswordEncrypted = "encrypted",
                    createdBy = createdBy,
                    expiresAt = Instant.now().plusSeconds(3600),
                ),
            )
    }
