package com.gsmsv.api.global.authorization

/** 규칙표(21번)에서 동작을 허용하는 팀 역할 범위. */
enum class Grant {
    ALL_MEMBERS,
    LEADER_OR_DEVOPS,
    LEADER_ONLY,
}

/** 권한 판단 대상 동작. [grant]가 규칙표의 한 줄이다. */
enum class Action(
    val grant: Grant,
) {
    VIEW(Grant.ALL_MEMBERS),
    VIEW_ACCESS_INFO(Grant.ALL_MEMBERS),
    EXTEND(Grant.ALL_MEMBERS),

    CREATE(Grant.LEADER_OR_DEVOPS),
    DELETE(Grant.LEADER_OR_DEVOPS),
    POWER(Grant.LEADER_OR_DEVOPS),
    RESIZE(Grant.LEADER_OR_DEVOPS),
    SNAPSHOT(Grant.LEADER_OR_DEVOPS),
    PORT_FORWARD(Grant.LEADER_OR_DEVOPS),
    FIREWALL(Grant.LEADER_OR_DEVOPS),
    DOMAIN(Grant.LEADER_OR_DEVOPS),

    INVITE_MEMBER(Grant.LEADER_ONLY),
    REMOVE_MEMBER(Grant.LEADER_ONLY),
    GRANT_DEVOPS(Grant.LEADER_ONLY),
    DISBAND_TEAM(Grant.LEADER_ONLY),
    TRANSFER_LEADER(Grant.LEADER_ONLY),
}
