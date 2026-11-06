package com.gsmsv.api.team.repository

import com.gsmsv.api.team.domain.Team
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface TeamRepository : JpaRepository<Team, String> {
    @Query(
        """
        select team from Team team
        where team.id in :teamIds
          and (:cursorCreatedAt is null
            or team.createdAt < :cursorCreatedAt
            or (team.createdAt = :cursorCreatedAt and team.id < :cursorId))
        order by team.createdAt desc, team.id desc
        """,
    )
    fun findPageByIds(
        @Param("teamIds") teamIds: Collection<String>,
        @Param("cursorCreatedAt") cursorCreatedAt: Instant?,
        @Param("cursorId") cursorId: String?,
        pageable: Pageable,
    ): List<Team>
}
