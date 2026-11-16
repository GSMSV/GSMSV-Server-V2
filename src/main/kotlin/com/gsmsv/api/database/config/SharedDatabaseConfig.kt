package com.gsmsv.api.database.config

import com.gsmsv.api.database.crypto.DatabasePasswordCipher
import com.gsmsv.api.database.provisioner.FakeDatabaseProvisioner
import com.gsmsv.api.database.provisioner.JdbcMysqlProvisioner
import com.gsmsv.api.database.provisioner.SharedDatabaseProvisioner
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * 공유 DB 서버 연결을 만든다.
 *
 * 실제 구현의 연결 풀은 `DataSource` 빈으로 내놓지 않는다. 내놓으면 앱 자체 데이터소스 자동 설정이 물러난다.
 */
@Configuration(proxyBeanMethods = false)
class SharedDatabaseConfig {
    @Bean
    @ConditionalOnProperty("gsmsv.database.shared.url")
    fun jdbcMysqlProvisioner(properties: SharedDatabaseProperties): SharedDatabaseProvisioner {
        check(properties.adminUsername.isNotBlank() && properties.adminPassword.isNotBlank()) {
            "gsmsv.database.shared.admin-username, admin-password도 함께 설정해야 합니다."
        }
        val pool =
            HikariDataSource().apply {
                jdbcUrl = properties.url
                username = properties.adminUsername
                password = properties.adminPassword
                // 관리 작업은 드물고 짧다
                maximumPoolSize = 2
                poolName = "shared-database-admin"
            }
        return JdbcMysqlProvisioner(pool)
    }

    /** 키는 환경 변수로만 받는다. 설정하지 않으면 빈이 없고, 비밀번호를 저장하는 기능이 기동에 실패한다 */
    @Bean
    @ConditionalOnProperty("gsmsv.database.encryption-key")
    fun databasePasswordCipher(
        @Value("\${gsmsv.database.encryption-key}") key: String,
    ): DatabasePasswordCipher = DatabasePasswordCipher(key)

    /** 공유 DB 서버를 설정하지 않은 로컬·개발에서 쓰는 모의 구현. 운영 프로필에서는 만들지 않는다 */
    @Bean
    @Profile("local", "dev")
    @ConditionalOnMissingBean(SharedDatabaseProvisioner::class)
    fun fakeDatabaseProvisioner(): SharedDatabaseProvisioner = FakeDatabaseProvisioner()
}
