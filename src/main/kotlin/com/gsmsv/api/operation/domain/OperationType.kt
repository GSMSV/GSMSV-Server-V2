package com.gsmsv.api.operation.domain

/**
 * 작업 종류. [value]는 응답의 `type`이고 Infra `Job.type`과 같은 이름이다 (Contracts-V2 `openapi/operation.yaml`).
 *
 * 앞부분은 대상 자원의 접두사와 같다. DB에는 enum 이름을 저장한다.
 */
enum class OperationType(
    val value: String,
) {
    VM_CREATE("vm.create"),
    VM_DELETE("vm.delete"),
    VM_POWER("vm.power"),
    VM_RESIZE("vm.resize"),
    SNAPSHOT_CREATE("snap.create"),
    SNAPSHOT_DELETE("snap.delete"),
    SNAPSHOT_RESTORE("snap.restore"),
    DATABASE_CREATE("db.create"),
    DATABASE_DELETE("db.delete"),
    BACKUP_RESTORE("backup.restore"),
    APP_CREATE("app.create"),
    APP_DELETE("app.delete"),
}
