package com.gsmsv.api.database.dto

import java.time.Instant

/** 7.5 접속 정보. 비밀번호가 들어 있으므로 응답에 `Cache-Control: no-store`를 붙인다 */
data class DatabaseCredentialResponse(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val jdbcUrl: String,
) {
    // 비밀번호가 로그·예외 메시지에 찍히지 않게 data class 기본 toString을 덮는다
    override fun toString(): String = "DatabaseCredentialResponse(database=$database, username=$username)"
}

/** 7.6 비밀번호 교체 결과 */
data class PasswordRotationResponse(
    val password: String,
    val rotatedAt: Instant,
    val reconnected: ReconnectedResponse,
) {
    override fun toString(): String = "PasswordRotationResponse(rotatedAt=$rotatedAt, reconnected=$reconnected)"
}

/** 새 접속 정보로 갱신한 서버리스 함수·원터치 배포 앱 개수 */
data class ReconnectedResponse(
    val functions: Int,
    val appCells: Int,
)
