package com.gsmsv.api.global.authorization

/**
 * 규칙표(21번)에서 동작을 허용하는 범위. 관리자는 어느 범위든 "모든 동작 허용" 규칙으로 통과한다 (22번 6장).
 *
 * [description]은 403 응답 message에 쓴다.
 */
enum class Grant(
    val description: String,
) {
    ALL_MEMBERS("팀원"),
    LEADER_OR_DEVOPS("팀장 또는 DevOps"),
    LEADER_ONLY("팀장"),

    /** 팀장, 또는 대상이 요청자 본인인 DevOps. 대상 사용자 id가 있어야 판단할 수 있다 */
    LEADER_OR_SELF_DEVOPS("팀장 또는 DevOps 본인"),

    /** 팀 역할로는 할 수 없다. 관리자 규칙으로만 통과한다 */
    ADMIN_ONLY("관리자"),
}

/**
 * 권한 판단 대상 동작. [grant]가 규칙표의 한 줄이다.
 *
 * [requiresApprovedTeam]이면 팀이 승인된 뒤에만 할 수 있다 (21번 4.1절). 팀 전용 로더가 검사한다.
 *
 * 동작 이름 형식(`vm.delete`처럼 자원 접두사를 붙이는지)과 목록은 Contracts-V2가 아직 정하지 않았다 (22번 13장).
 * 정해지면 이 enum을 그 목록에 맞춘다.
 */
enum class Action(
    val grant: Grant,
    val requiresApprovedTeam: Boolean = false,
) {
    VIEW(Grant.ALL_MEMBERS),
    VIEW_ACCESS_INFO(Grant.ALL_MEMBERS),
    EXTEND(Grant.ALL_MEMBERS),

    /** 백업 파일 내려받기. 계약 `permissions`의 `read_backup`과 이름을 맞췄다 */
    READ_BACKUP(Grant.ALL_MEMBERS),

    /** 팀 탈퇴. 대상은 언제나 요청자 본인이다 */
    LEAVE_TEAM(Grant.ALL_MEMBERS),

    // 계약은 승인 대기 팀이 VM·데이터베이스를 만들지 못하게 한다. 지금은 생성 동작이 자원 종류로 나뉘지 않아
    // 모든 생성에 적용한다. 동작 이름이 자원별로 나뉘면 VM·데이터베이스 생성에만 둔다
    CREATE(Grant.LEADER_OR_DEVOPS, requiresApprovedTeam = true),
    UPDATE(Grant.LEADER_OR_DEVOPS),
    DELETE(Grant.LEADER_OR_DEVOPS),
    POWER(Grant.LEADER_OR_DEVOPS),
    RESIZE(Grant.LEADER_OR_DEVOPS),
    SNAPSHOT(Grant.LEADER_OR_DEVOPS),
    PORT_FORWARD(Grant.LEADER_OR_DEVOPS),
    FIREWALL(Grant.LEADER_OR_DEVOPS),
    DOMAIN(Grant.LEADER_OR_DEVOPS),

    INVITE_MEMBER(Grant.LEADER_ONLY),
    RESUBMIT_TEAM(Grant.LEADER_ONLY),
    REMOVE_MEMBER(Grant.LEADER_ONLY),
    GRANT_DEVOPS(Grant.LEADER_ONLY),
    DISBAND_TEAM(Grant.LEADER_ONLY),
    TRANSFER_LEADER(Grant.LEADER_ONLY),

    REVOKE_DEVOPS(Grant.LEADER_OR_SELF_DEVOPS),

    APPROVE_TEAM(Grant.ADMIN_ONLY),
    APPROVE_DISBAND_TEAM(Grant.ADMIN_ONLY),
    ;

    /**
     * `permissions` 응답의 키. 키 형식(`delete` / `vm.delete`)은 Contracts-V2가 아직 정하지 않았다 (api-rules 3장).
     * 지금은 JSON 필드와 같은 snake_case 소문자로 둔다 (예: `view_access_info`)
     */
    val permissionKey: String
        get() = name.lowercase()
}
