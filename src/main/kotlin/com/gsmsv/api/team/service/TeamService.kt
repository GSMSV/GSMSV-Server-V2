package com.gsmsv.api.team.service

import com.gsmsv.api.global.authorization.TeamMembershipReader
import com.gsmsv.api.global.authorization.TeamReader
import com.gsmsv.api.global.authorization.TeamRole
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.response.FieldErrorDetail
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.domain.TeamStatus
import com.gsmsv.api.team.repository.TeamMemberRepository
import com.gsmsv.api.team.repository.TeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TeamService(
    private val teamRepository: TeamRepository,
    private val teamMemberRepository: TeamMemberRepository,
) : TeamReader<Team>,
    TeamMembershipReader {
    @Transactional(readOnly = true)
    override fun findById(id: String): Team? = teamRepository.findById(id).orElse(null)

    @Transactional(readOnly = true)
    override fun findRole(
        teamId: String,
        userId: String,
    ): TeamRole? = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)?.role

    @Transactional(readOnly = true)
    override fun findMemberships(userId: String): Map<String, TeamRole> =
        teamMemberRepository.findAllByUserId(userId).associate { it.teamId to it.role }

    /** 팀원 전원의 사용자 id. 알림처럼 팀 앞으로 온 일을 팀원에게 나눌 때 쓴다 */
    @Transactional(readOnly = true)
    fun findMemberUserIds(teamId: String): List<String> = teamMemberRepository.findAllByTeamId(teamId).map { it.userId }

    @Transactional
    fun resubmit(
        team: Team,
        name: String?,
    ): Team {
        val normalizedName = name?.trim()
        if (normalizedName != null && normalizedName.codePointCount(0, normalizedName.length) !in 1..30) {
            throw BusinessException(
                ErrorCode.VALIDATION_FAILED,
                details = mapOf("fields" to listOf(FieldErrorDetail("name", "팀 이름은 공백 제거 후 1~30자여야 합니다."))),
            )
        }
        if (team.status != TeamStatus.REJECTED) throw BusinessException(ErrorCode.CONFLICT)
        team.resubmit(normalizedName)
        return team
    }
}
