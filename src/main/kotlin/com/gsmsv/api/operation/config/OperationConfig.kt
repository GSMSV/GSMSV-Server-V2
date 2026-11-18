package com.gsmsv.api.operation.config

import com.gsmsv.api.infra.job.JobInfraClient
import com.gsmsv.api.operation.repository.OperationRepository
import com.gsmsv.api.operation.service.OperationJobPoller
import com.gsmsv.api.operation.service.OperationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.PlatformTransactionManager

/** 폴러는 `gsmsv.operation.job-poll-enabled=false`로 끌 수 있다 (테스트 등) */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class OperationConfig {
    @Bean
    @ConditionalOnProperty("gsmsv.operation.job-poll-enabled", havingValue = "true", matchIfMissing = true)
    fun operationJobPoller(
        operationRepository: OperationRepository,
        operationService: OperationService,
        jobInfraClient: JobInfraClient,
        properties: OperationProperties,
        jdbcTemplate: JdbcTemplate,
        transactionManager: PlatformTransactionManager,
    ) = OperationJobPoller(operationRepository, operationService, jobInfraClient, properties, jdbcTemplate, transactionManager)
}
