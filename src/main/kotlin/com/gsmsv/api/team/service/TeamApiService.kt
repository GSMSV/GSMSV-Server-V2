package com.gsmsv.api.team.service

import com.gsmsv.api.global.authorization.TeamRole
import com.gsmsv.api.global.config.QuotaProperties
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.response.Cursor
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.global.response.FieldErrorDetail
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.domain.TeamMember
import com.gsmsv.api.team.dto.CreateTeamResponse
import com.gsmsv.api.team.dto.QuotaUsageResponse
import com.gsmsv.api.team.dto.TeamDetailResponse
import com.gsmsv.api.team.dto.TeamMemberResponse
import com.gsmsv.api.team.dto.TeamQuotaResponse
import com.gsmsv.api.team.dto.TeamSummaryResponse
import com.gsmsv.api.team.dto.VmQuotaResponse
import com.gsmsv.api.team.repository.TeamMemberRepository
import com.gsmsv.api.team.repository.TeamRepository
import com.gsmsv.api.user.service.UserService
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TeamApiService(
    private val teamRepository: TeamRepository,
    private val teamMemberRepository: TeamMemberRepository,
    private val userService: UserService,
    private val quotaProperties: QuotaProperties,
    private val teamVmUsageReader: TeamVmUsageReader,
) {
    @Transactional
    fun createTeam(
        userId: String,
        name: String,
    ): CreateTeamResponse {
        val normalizedName = name.trim()
        val nameLength = normalizedName.codePointCount(0, normalizedName.length)
        if (nameLength !in MIN_TEAM_NAME_LENGTH..MAX_TEAM_NAME_LENGTH) {
            throw BusinessException(
                ErrorCode.VALIDATION_FAILED,
                details = mapOf("fields" to listOf(FieldErrorDetail("name", "팀 이름은 공백 제거 후 1~30자여야 합니다."))),
            )
        }

        val team = teamRepository.save(Team(IdGenerator.teamId(), normalizedName))
        teamMemberRepository.save(TeamMember(IdGenerator.teamMemberId(), team.id, userId, TeamRole.LEADER))
        return CreateTeamResponse.from(team)
    }

    @Transactional(readOnly = true)
    fun listMyTeams(
        userId: String,
        pageRequest: CursorPageRequest,
    ): CursorPage<TeamSummaryResponse> {
        val cursor = pageRequest.decodedCursor()
        val memberships = teamMemberRepository.findAllByUserId(userId)
        val teamIds = memberships.map { it.teamId }.distinct()
        if (teamIds.isEmpty()) return CursorPage(emptyList(), null)

        val rows =
            teamRepository.findPageByIds(
                teamIds,
                cursor?.createdAt,
                cursor?.id,
                PageRequest.of(0, pageRequest.limit + 1),
            )
        val pageTeamIds = rows.take(pageRequest.limit).map { it.id }
        val memberCounts =
            if (pageTeamIds.isEmpty()) {
                emptyMap()
            } else {
                teamMemberRepository.countByTeamIds(pageTeamIds).associate { it.getTeamId() to it.getMemberCount() }
            }
        val roles = memberships.associate { it.teamId to it.role }

        return CursorPage.of(rows, pageRequest.limit, { Cursor(it.createdAt, it.id) }) { team ->
            TeamSummaryResponse(
                id = team.id,
                name = team.name,
                status = team.status.name.lowercase(),
                myRole = roles.getValue(team.id).name.lowercase(),
                memberCount = memberCounts.getValue(team.id).toInt(),
            )
        }
    }

    @Transactional(readOnly = true)
    fun getTeamDetail(
        userId: String,
        team: Team,
        permissions: Map<String, Boolean>,
    ): TeamDetailResponse {
        val members = teamMemberRepository.findAllByTeamIdOrderByIdAsc(team.id)
        val usersById = userService.findSummariesByIds(members.map { it.userId })
        val memberResponses =
            members.map { member ->
                val user = usersById.getValue(member.userId)
                TeamMemberResponse(
                    memberId = member.id,
                    name = user.name,
                    email = user.email,
                    role = member.role.name.lowercase(),
                )
            }

        return TeamDetailResponse(
            id = team.id,
            name = team.name,
            status = team.status.name.lowercase(),
            rejectionReason = team.rejectionReason,
            myRole =
                members
                    .firstOrNull { it.userId == userId }
                    ?.role
                    ?.name
                    ?.lowercase(),
            members = memberResponses,
            quota = if (team.approved) teamQuota(team.id) else null,
            permissions = permissions,
        )
    }

    private fun teamQuota(teamId: String): TeamQuotaResponse {
        val usage = teamVmUsageReader.readUsage(teamId)
        return TeamQuotaResponse(
            vm =
                VmQuotaResponse(
                    vcpu = QuotaUsageResponse(used = usage.vcpu, limit = quotaProperties.team.vcpu),
                    ramGib = QuotaUsageResponse(used = usage.ramGib, limit = quotaProperties.team.ramGib),
                    ssdGib = QuotaUsageResponse(used = usage.ssdGib, limit = quotaProperties.team.ssdGib),
                ),
        )
    }

    companion object {
        private const val MIN_TEAM_NAME_LENGTH = 1
        private const val MAX_TEAM_NAME_LENGTH = 30
    }
}
