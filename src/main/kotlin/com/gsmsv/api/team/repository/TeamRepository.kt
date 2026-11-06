package com.gsmsv.api.team.repository

import com.gsmsv.api.team.domain.Team
import org.springframework.data.jpa.repository.JpaRepository

interface TeamRepository : JpaRepository<Team, String>
