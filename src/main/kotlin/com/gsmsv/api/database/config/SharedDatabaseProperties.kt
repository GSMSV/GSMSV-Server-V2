package com.gsmsv.api.database.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 공유 DB 서버(MySQL) 관리 연결 설정 (24번 DBaaS 7.2). 앱 자체 데이터소스와 별개다.
 *
 * 값은 환경 변수로만 받는다. `@ConfigurationPropertiesScan`이 항상 묶기 때문에 빈 문자열을 기본으로 두고,
 * [url]이 있을 때만 [SharedDatabaseConfig]가 실제 구현을 만들면서 세 값이 모두 있는지 검사한다.
 */
@ConfigurationProperties("gsmsv.database.shared")
data class SharedDatabaseProperties(
    /** JDBC 주소. 서버 주소가 들어 있으므로 코드·로그에 쓰지 않는다 */
    val url: String = "",
    val adminUsername: String = "",
    val adminPassword: String = "",
) {
    // 관리 비밀번호가 로그에 찍히지 않게 한다
    override fun toString(): String = "SharedDatabaseProperties(url=***, adminUsername=***, adminPassword=***)"
}
