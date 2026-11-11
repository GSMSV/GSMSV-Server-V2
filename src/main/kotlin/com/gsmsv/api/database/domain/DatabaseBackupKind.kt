package com.gsmsv.api.database.domain

enum class DatabaseBackupKind {
    /** 매일 자동으로 만드는 백업 */
    DAILY,

    /** 백업에서 복원하기 직전의 상태를 남기는 백업 (7.12) */
    PRE_RESTORE,
}
