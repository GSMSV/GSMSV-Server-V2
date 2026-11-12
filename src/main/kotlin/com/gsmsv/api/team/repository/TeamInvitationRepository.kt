package com.gsmsv.api.team.repository

import com.gsmsv.api.team.domain.TeamInvitation
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface TeamInvitationRepository : JpaRepository<TeamInvitation, String> {
    fun saveAndFlush(entity: TeamInvitation): TeamInvitation

    fun findByTeamIdAndEmailAndAcceptedAtIsNullAndCanceledAtIsNull(
        teamId: String,
        email: String,
    ): TeamInvitation?

    @Query(
        """
        select i from TeamInvitation i
        where i.teamId = :teamId and i.acceptedAt is null and i.canceledAt is null and i.expiresAt > :now
          and (:cursorTime is null or i.sentAt < :cursorTime or (i.sentAt = :cursorTime and i.id < :cursorId))
        order by i.sentAt desc, i.id desc
        """,
    )
    fun findActivePage(
        @Param("teamId") teamId: String,
        @Param("now") now: Instant,
        @Param("cursorTime") cursorTime: Instant?,
        @Param("cursorId") cursorId: String?,
        pageable: Pageable,
    ): List<TeamInvitation>

    fun findByIdAndTeamId(
        id: String,
        teamId: String,
    ): TeamInvitation?
}
