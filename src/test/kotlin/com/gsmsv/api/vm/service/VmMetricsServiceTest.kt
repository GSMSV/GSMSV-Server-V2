package com.gsmsv.api.vm.service

import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.response.FieldErrorDetail
import com.gsmsv.api.infra.vm.VmMetricSample
import com.gsmsv.api.infra.vm.VmMetricsInfraClient
import com.gsmsv.api.infra.vm.VmMetricsPeriod
import com.gsmsv.api.vm.domain.Vm
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VmMetricsServiceTest {
    private val client = mockk<VmMetricsInfraClient>()
    private val service = VmMetricsService(client)

    @Test
    fun `period가 없으면 hour로 묻고 명세 칸 이름대로 바꾼다`() {
        val vm = activeVm()
        every { client.getMetrics(vm.id, VmMetricsPeriod.HOUR) } returns listOf(SAMPLE, STOPPED)

        val response = service.get(vm, null)

        assertEquals("hour", response.period)
        assertEquals(2, response.points.size)
        val point = response.points[0]
        assertEquals(SAMPLE.at, point.at)
        assertEquals(3.1, point.cpuPercent)
        assertEquals(1650.0, point.memoryMb)
        assertEquals(12000.0, point.netInBps)
        assertEquals(8000.0, point.netOutBps)
        assertEquals(0.0, point.diskReadBps)
        assertEquals(4096.0, point.diskWriteBps)
        assertNull(response.points[1].cpuPercent)
    }

    @Test
    fun `day면 day로 묻는다`() {
        val vm = activeVm()
        every { client.getMetrics(vm.id, VmMetricsPeriod.DAY) } returns emptyList()

        assertEquals("day", service.get(vm, "day").period)
    }

    @Test
    fun `hour·day가 아니면 묻지 않고 VALIDATION_FAILED다`() {
        listOf("week", "HOUR", "").forEach { period ->
            val error = assertThrows<BusinessException> { service.get(activeVm(), period) }

            assertEquals(ErrorCode.VALIDATION_FAILED, error.errorCode)
            assertEquals(listOf("period"), (error.details?.get("fields") as List<*>).map { (it as FieldErrorDetail).field })
        }
        verify(exactly = 0) { client.getMetrics(any(), any()) }
    }

    @Test
    fun `정지된 VM도 기록을 묻는다`() {
        val vm = activeVm().also { it.suspend() }
        every { client.getMetrics(vm.id, VmMetricsPeriod.HOUR) } returns listOf(STOPPED)

        assertEquals(1, service.get(vm, "hour").points.size)
    }

    @Test
    fun `만드는 중이거나 만들기에 실패한 VM은 묻지 않고 빈 목록이다`() {
        val creating = vm()
        val failed = vm().also { it.markFailed() }

        assertEquals(emptyList(), service.get(creating, null).points)
        assertEquals(emptyList(), service.get(failed, "day").points)
        verify(exactly = 0) { client.getMetrics(any(), any()) }
    }

    @Test
    fun `가상화 서버가 응답하지 않으면 503을 그대로 올린다`() {
        val vm = activeVm()
        every { client.getMetrics(vm.id, any()) } throws BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE)

        val error = assertThrows<BusinessException> { service.get(vm, null) }

        assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, error.errorCode)
    }

    private fun activeVm() = vm().also { it.markActive("192.0.2.10") }

    private fun vm() =
        Vm.create(
            id = "vm-k3m9x2p7q4ta",
            name = "테스트 VM",
            purpose = null,
            owner = Owner.User("user-a1b2c3d4e5f6"),
            image = "ubuntu-24.04",
            vcpu = 1,
            ramGb = 1,
            ssdGb = 10,
            initialUsername = "ubuntu",
            initialPasswordEncrypted = "encrypted",
            createdBy = "user-a1b2c3d4e5f6",
            expiresAt = Instant.parse("2027-01-01T00:00:00Z"),
        )

    private companion object {
        val SAMPLE = VmMetricSample(Instant.parse("2026-11-24T09:00:00Z"), 3.1, 1650.0, 12000.0, 8000.0, 0.0, 4096.0)
        val STOPPED = VmMetricSample(Instant.parse("2026-11-24T09:01:00Z"), null, null, null, null, null, null)
    }
}
