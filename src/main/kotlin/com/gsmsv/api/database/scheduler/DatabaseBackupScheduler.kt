package com.gsmsv.api.database.scheduler

import com.gsmsv.api.database.service.DatabaseBackupService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

private val log = KotlinLogging.logger {}

/**
 * 일일 백업과 만료 정리를 돌린다. 인스턴스가 여러 개여도 한 곳에서만 돌도록 DB 어드바이저리 잠금을 쓴다.
 * 잠금은 트랜잭션이 끝나면 풀리므로, 돌던 인스턴스가 죽어도 잠금이 남지 않는다.
 */
class DatabaseBackupScheduler(
    private val service: DatabaseBackupService,
    private val jdbcTemplate: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
) {
    private val transaction = TransactionTemplate(transactionManager)

    @Scheduled(cron = "\${gsmsv.database.backup.cron:0 0 18 * * *}", zone = "UTC")
    fun run() {
        transaction.executeWithoutResult {
            val locked = jdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean::class.java, LOCK_KEY)
            if (locked != true) {
                log.info { "다른 인스턴스가 DB 백업을 돌리고 있어 건너뜁니다." }
                return@executeWithoutResult
            }
            val created = service.backupAll()
            val deleted = service.deleteExpired()
            log.info { "DB 백업 작업 완료. 만든 백업=$created 정리한 백업=$deleted" }
        }
    }

    private companion object {
        /** 앱 안에서 겹치지 않는 임의의 잠금 번호 */
        const val LOCK_KEY = 7_770_077L
    }
}
