package com.gsmsv.api.user.dto

import com.gsmsv.api.global.authorization.TeamRole
import com.gsmsv.api.user.domain.User
import java.time.Instant

data class MeResponse(
    val id: String,
    val email: String,
    val name: String,
    val school: SchoolResponse,
    val role: String,
    val joinedAt: Instant,
    val profileImageUrl: String?,
    val teams: List<MyTeamResponse>,
    val connections: ConnectionsResponse,
)

data class SchoolResponse(
    val grade: Int?,
    val classNumber: Int?,
    val number: Int?,
    val major: String?,
)

data class MyTeamResponse(
    val id: String,
    val name: String,
    val myRole: String,
    val status: String,
)

data class ConnectionsResponse(
    val github: Boolean,
    val discord: Boolean,
)

fun User.toMeResponse(
    teams: List<MyTeamResponse>,
    githubConnected: Boolean,
    discordConnected: Boolean,
): MeResponse =
    MeResponse(
        id = id,
        email = email,
        name = name,
        school = SchoolResponse(grade, classNumber, studentNumber, major),
        role = role.name.lowercase(),
        joinedAt = createdAt,
        profileImageUrl = profileImageFileName?.let { "/api/v2/files/profile-images/$it" },
        teams = teams,
        connections = ConnectionsResponse(githubConnected, discordConnected),
    )
