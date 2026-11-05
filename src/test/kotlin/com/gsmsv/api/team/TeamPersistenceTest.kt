package com.gsmsv.api.team

import com.gsmsv.api.TestcontainersConfiguration
import com.gsmsv.api.global.authorization.TeamLoader
import com.gsmsv.api.global.authorization.TeamRole
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.domain.TeamMember
import com.gsmsv.api.team.repository.TeamMemberRepository
import com.gsmsv.api.team.repository.TeamRepository
import com.gsmsv.api.team.service.TeamService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="])
@Tag("integration")
@Transactional
class TeamPersistenceTest
    @Autowired
    constructor(
        private val teamRepository: TeamRepository,
        private val teamMemberRepository: TeamMemberRepository,
        private val teamService: TeamService,
        private val teamLoader: TeamLoader,
    ) {
        @Test
        fun `팀과 멤버십을 저장하고 권한 로더에 연결한다`() {
            val teamId = IdGenerator.teamId()
            val userId = "user-a1b2c3d4e5f6"
            teamRepository.saveAndFlush(Team(teamId, "통합 테스트 팀"))
            teamMemberRepository.saveAndFlush(TeamMember(IdGenerator.teamMemberId(), teamId, userId, TeamRole.LEADER))

            val team = teamService.findById(teamId)

            assertNotNull(team)
            assertEquals(teamId, team.id)
            assertTrue(team.approved.not())
            assertEquals(TeamRole.LEADER, teamService.findRole(teamId, userId))
            assertEquals(mapOf(teamId to TeamRole.LEADER), teamService.findMemberships(userId).filterKeys { it == teamId })
            assertNotNull(teamLoader)
        }

        @Test
        fun `한 팀에는 팀장이 한 명만 저장된다`() {
            val teamId = IdGenerator.teamId()
            teamRepository.saveAndFlush(Team(teamId, "팀장 중복 테스트 팀"))
            teamMemberRepository.saveAndFlush(TeamMember(IdGenerator.teamMemberId(), teamId, USER_ID, TeamRole.LEADER))

            assertFailsWith<DataIntegrityViolationException> {
                teamMemberRepository.saveAndFlush(
                    TeamMember(IdGenerator.teamMemberId(), teamId, OTHER_USER_ID, TeamRole.LEADER),
                )
            }
        }

        @Test
        fun `한 사용자는 같은 팀에 중복 등록할 수 없다`() {
            val teamId = IdGenerator.teamId()
            teamRepository.saveAndFlush(Team(teamId, "멤버 중복 테스트 팀"))
            teamMemberRepository.saveAndFlush(TeamMember(IdGenerator.teamMemberId(), teamId, USER_ID, TeamRole.LEADER))

            assertFailsWith<DataIntegrityViolationException> {
                teamMemberRepository.saveAndFlush(
                    TeamMember(IdGenerator.teamMemberId(), teamId, USER_ID, TeamRole.MEMBER),
                )
            }
        }

        companion object {
            private const val USER_ID = "user-a1b2c3d4e5f6"
            private const val OTHER_USER_ID = "user-b2c3d4e5f6g7"
        }
    }
