package com.gsmsv.api.operation.service

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.infra.job.JobInfraClient
import com.gsmsv.api.operation.config.OperationProperties
import com.gsmsv.api.operation.repository.OperationRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

private val log = KotlinLogging.logger {}

/**
 * 진행 중인 operation의 Infra 작업 상태를 폴링해 옮겨 적고, 보관 기간이 지난 기록을 지운다 (Kotlin·Go 경계 6.1).
 *
 * 인스턴스가 여러 개여도 한 곳에서만 돌도록 DB 어드바이저리 잠금을 쓴다. operation 하나를 처리하다 실패해도
 * 다른 operation은 계속 처리하도록 하나씩 따로 커밋한다.
 */
class OperationJobPoller(
    private val operationRepository: OperationRepository,
    private val operationService: OperationService,
    private val jobInfraClient: JobInfraClient,
    private val properties: OperationProperties,
    private val jdbcTemplate: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val lockTransaction = TransactionTemplate(transactionManager)
    private val itemTransaction =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    @Scheduled(fixedDelayString = "\${gsmsv.operation.job-poll-interval:PT2S}")
    fun poll() {
        withLock(POLL_LOCK_KEY) {
            val active = operationRepository.findAllByFinishedAtIsNullAndJobIdIsNotNull()
            for (operation in active) {
                val jobId = operation.jobId ?: continue
                pollOne(operation.id, jobId)
            }
        }
    }

    @Scheduled(cron = "\${gsmsv.operation.cleanup-cron:0 30 18 * * *}", zone = "UTC")
    fun deleteExpired() {
        withLock(CLEANUP_LOCK_KEY) {
            val deleted = operationRepository.deleteFinishedBefore(clock.instant().minus(properties.retention))
            if (deleted > 0) log.info { "보관 기간이 지난 operation을 지웠다: $deleted" }
        }
    }

    private fun pollOne(
        operationId: String,
        jobId: String,
    ) {
        try {
            val job = jobInfraClient.getJob(jobId)
            itemTransaction.executeWithoutResult {
                if (job == null) operationService.markJobLost(operationId) else operationService.applyJob(operationId, job)
            }
        } catch (e: BusinessException) {
            // Infra가 잠시 응답하지 않는 경우다. 다음 폴링에서 다시 묻는다
            log.warn { "Infra 작업 조회 실패: operationId=$operationId, code=${e.errorCode}" }
        } catch (e: RuntimeException) {
            log.error(e) { "operation 갱신 실패: operationId=$operationId" }
        }
    }

    private fun withLock(
        key: Long,
        block: () -> Unit,
    ) {
        lockTransaction.executeWithoutResult {
            val locked = jdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean::class.java, key)
            if (locked == true) block()
        }
    }

    private companion object {
        /** 앱 안에서 겹치지 않는 임의의 잠금 번호 */
        const val POLL_LOCK_KEY = 7_770_078L
        const val CLEANUP_LOCK_KEY = 7_770_079L
    }
}
