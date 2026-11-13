package com.gsmsv.api.audit.domain

/**
 * 감사 대상 동작. 비밀번호를 보여 주거나 파일을 내보내거나 되돌릴 수 없는 변경을 하는 동작이다.
 *
 * DB 칸에는 이름을 문자열로 저장하고 CHECK를 두지 않아, 동작을 추가해도 마이그레이션이 필요 없다.
 * 동작 이름 형식은 Contracts-V2가 아직 정하지 않았다 (authorization 13장). 정해지면 이름을 맞추고
 * 이미 저장된 값은 새 마이그레이션으로 옮긴다.
 */
enum class AuditEventType {
    /** 데이터베이스 접속 정보(비밀번호 포함)를 봤다 */
    DATABASE_CREDENTIALS_READ,
    DATABASE_PASSWORD_ROTATE,
    DATABASE_DELETE,
    DATABASE_BACKUP_DOWNLOAD,
    DATABASE_BACKUP_RESTORE,

    /** 관리자가 삭제된 데이터베이스를 백업에서 되살렸다 */
    DATABASE_DELETED_RECOVER,
}
