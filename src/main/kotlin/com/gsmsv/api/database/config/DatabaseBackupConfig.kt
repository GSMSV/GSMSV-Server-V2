package com.gsmsv.api.database.config

import com.gsmsv.api.database.backup.BackupStorage
import com.gsmsv.api.database.backup.DatabaseDumper
import com.gsmsv.api.database.backup.LocalFileBackupStorage
import com.gsmsv.api.database.backup.MysqlCliDatabaseDumper
import com.gsmsv.api.database.repository.DatabaseBackupRepository
import com.gsmsv.api.database.repository.DatabaseRepository
import com.gsmsv.api.database.scheduler.DatabaseBackupScheduler
import com.gsmsv.api.database.service.DatabaseBackupService
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.PlatformTransactionManager
import java.nio.file.Path

/** `gsmsv.database.backup.directory`를 설정했을 때만 백업 기능을 켠다 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty("gsmsv.database.backup.directory")
class DatabaseBackupConfig {
    @Bean
    @ConditionalOnMissingBean(BackupStorage::class)
    fun backupStorage(properties: DatabaseBackupProperties): BackupStorage = LocalFileBackupStorage(Path.of(properties.directory))

    @Bean
    @ConditionalOnMissingBean(DatabaseDumper::class)
    fun databaseDumper(properties: SharedDatabaseProperties): DatabaseDumper {
        check(properties.url.isNotBlank()) { "백업을 켜려면 gsmsv.database.shared 설정이 필요합니다." }
        return MysqlCliDatabaseDumper(properties)
    }

    @Bean
    fun databaseBackupService(
        databaseRepository: DatabaseRepository,
        backupRepository: DatabaseBackupRepository,
        storage: BackupStorage,
        dumper: DatabaseDumper,
        properties: DatabaseBackupProperties,
    ) = DatabaseBackupService(databaseRepository, backupRepository, storage, dumper, properties)

    @Bean
    fun databaseBackupScheduler(
        service: DatabaseBackupService,
        jdbcTemplate: JdbcTemplate,
        transactionManager: PlatformTransactionManager,
    ) = DatabaseBackupScheduler(service, jdbcTemplate, transactionManager)
}
