package com.gsmsv.api.global.authorization

/** 팀 안의 역할. [description]은 403 응답 message에 쓴다. */
enum class TeamRole(
    val description: String,
) {
    LEADER("팀장"),
    DEVOPS("DevOps"),
    MEMBER("일반 팀원"),
}
