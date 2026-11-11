package com.gsmsv.api.database.provisioner

/**
 * 공유 DB 서버 안의 database와 계정을 직접 만들고 바꾸고 지운다 (24번 DBaaS 7.2).
 *
 * 권한 판단은 하지 않는다. 호출은 로더를 통과한 service에서만 한다.
 * 서버 쪽 database·계정 이름은 DB 자원 id(`db-…`)를 그대로 쓰고 사용자 입력은 SQL 식별자에 넣지 않는다.
 * 실패하면 [DatabaseProvisioningException]을 던진다.
 */
interface SharedDatabaseProvisioner {
    /**
     * database와 계정을 만들고 그 database 권한만 주며 동시 연결 수를 [maxConnections]로 건다.
     * 중간에 실패하면 이미 만든 것을 정리하고 예외를 던진다.
     */
    fun create(
        databaseId: String,
        password: String,
        maxConnections: Int,
    )

    /** 비밀번호를 바꾸고 그 계정의 기존 연결을 모두 끊는다 (7.6) */
    fun rotatePassword(
        databaseId: String,
        newPassword: String,
    )

    /** 계정을 잠그고 기존 연결을 끊는다. 데이터는 남긴다 (만료 정지) */
    fun lock(databaseId: String)

    /** 잠근 계정을 푼다 (7.9 연장) */
    fun unlock(databaseId: String)

    /** database와 계정을 지운다. 이미 없어도 오류가 아니다 */
    fun drop(databaseId: String)
}

class DatabaseProvisioningException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
