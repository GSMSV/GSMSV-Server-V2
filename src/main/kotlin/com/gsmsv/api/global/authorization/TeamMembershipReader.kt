package com.gsmsv.api.global.authorization

/**
 * 팀 멤버십 조회 포트. 팀 도메인이 구현한다.
 *
 * 구현체는 결과를 캐시하지 않는다. 제외·역할 변경이 바로 권한 판단에 반영돼야 한다 (22번 규칙).
 */
interface TeamMembershipReader {
    /** 팀에서 사용자의 역할. 팀원이 아니면 null */
    fun findRole(
        teamId: String,
        userId: String,
    ): TeamRole?

    /** 사용자가 속한 팀 id와 역할 */
    fun findMemberships(userId: String): Map<String, TeamRole>
}
