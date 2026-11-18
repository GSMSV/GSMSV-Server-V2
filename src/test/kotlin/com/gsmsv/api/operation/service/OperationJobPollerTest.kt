package com.gsmsv.api.operation.service

import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.infra.job.InfraJob
import com.gsmsv.api.infra.job.JobInfraClient
import com.gsmsv.api.operation.config.OperationProperties
import com.gsmsv.api.operation.domain.Operation
import com.gsmsv.api.operation.domain.OperationType
import com.gsmsv.api.operation.repository.OperationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class OperationJobPollerTest {
    private val now = Instant.parse("2026-11-12T09:00:00Z")
    private val repository = mockk<OperationRepository>()
    private val service = mockk<OperationService>(relaxed = true)
    private val client = mockk<JobInfraClient>()
    private val jdbcTemplate = mockk<JdbcTemplate>()
    private val poller =
        OperationJobPoller(
            repository,
            service,
            client,
            OperationProperties(),
            jdbcTemplate,
            mockk<PlatformTransactionManager>(relaxed = true),
            Clock.fixed(now, ZoneOffset.UTC),
        )

    private fun locked(value: Boolean) {
        every { jdbcTemplate.queryForObject(any<String>(), Boolean::class.java, any()) } returns value
    }

    private fun active(
        id: String,
        jobId: String,
    ) = Operation(
        id,
        OperationType.VM_CREATE,
        "grn:team-4p8w2k9m1xza:vm-k3m9x2p7q4ta",
        Owner.Team("team-4p8w2k9m1xza"),
        "user-a1b2c3d4e5f6",
        now,
    ).apply { attachJob(jobId, now) }

    private fun job(jobId: String) = InfraJob(jobId, "vm.create", "vm-k3m9x2p7q4ta", "running", "cloning", 0)

    @Test
    fun `진행 중인 작업마다 Infra 상태를 옮겨 적고 기록이 없으면 잃은 작업으로 마무리한다`() {
        locked(true)
        every { repository.findAllByFinishedAtIsNullAndJobIdIsNotNull() } returns
            listOf(active("op-7w8k9q0p1a2s", "job-1"), active("op-8x9k2q3p4a5s", "job-2"))
        every { client.getJob("job-1") } returns job("job-1")
        every { client.getJob("job-2") } returns null

        poller.poll()

        verify { service.applyJob("op-7w8k9q0p1a2s", job("job-1")) }
        verify { service.markJobLost("op-8x9k2q3p4a5s") }
    }

    @Test
    fun `한 작업에서 Infra 조회가 실패해도 다음 작업은 계속 처리하고 실패한 작업은 건드리지 않는다`() {
        locked(true)
        every { repository.findAllByFinishedAtIsNullAndJobIdIsNotNull() } returns
            listOf(active("op-7w8k9q0p1a2s", "job-1"), active("op-8x9k2q3p4a5s", "job-2"))
        every { client.getJob("job-1") } throws BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE)
        every { client.getJob("job-2") } returns job("job-2")

        poller.poll()

        verify(exactly = 0) { service.applyJob("op-7w8k9q0p1a2s", any()) }
        verify(exactly = 0) { service.markJobLost(any()) }
        verify { service.applyJob("op-8x9k2q3p4a5s", job("job-2")) }
    }

    @Test
    fun `다른 인스턴스가 잠금을 잡고 있으면 아무것도 하지 않는다`() {
        locked(false)

        poller.poll()
        poller.deleteExpired()

        verify(exactly = 0) { repository.findAllByFinishedAtIsNullAndJobIdIsNotNull() }
        verify(exactly = 0) { repository.deleteFinishedBefore(any()) }
    }

    @Test
    fun `끝난 지 보관 기간이 지난 기록을 지운다`() {
        locked(true)
        every { repository.deleteFinishedBefore(any()) } returns 3

        poller.deleteExpired()

        verify { repository.deleteFinishedBefore(now.minus(Duration.ofDays(30))) }
    }
}
