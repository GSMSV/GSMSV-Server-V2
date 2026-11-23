package com.gsmsv.api.vm.service

import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.infra.vm.VmLiveState
import com.gsmsv.api.infra.vm.VmPower
import com.gsmsv.api.infra.vm.VmStateInfraClient
import com.gsmsv.api.vm.domain.Vm
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class VmLiveStatusReaderTest {
    private val client = mockk<VmStateInfraClient>()
    private var now = Instant.parse("2026-11-23T00:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId?) = this

            override fun instant() = now
        }
    private val reader = VmLiveStatusReader(client, clock)

    @Test
    fun `받은 전원 상태와 사용률을 돌려준다`() {
        val vm = activeVm()
        every { client.getState(vm.id) } returns STOPPED

        val status = reader.read(vm)

        assertEquals(VmPowerState.STOPPED, status.powerState)
        assertEquals(STOPPED, status.usage)
    }

    @Test
    fun `10초 안에는 캐시한 값을 쓰고 지나면 다시 묻는다`() {
        val vm = activeVm()
        every { client.getState(vm.id) } returns RUNNING

        reader.read(vm)
        now = now.plus(Duration.ofSeconds(9))
        reader.read(vm)
        verify(exactly = 1) { client.getState(vm.id) }

        now = now.plus(Duration.ofSeconds(1))
        reader.read(vm)
        verify(exactly = 2) { client.getState(vm.id) }
    }

    @Test
    fun `조회가 실패하거나 가상화 서버에 VM이 없으면 unknown이다`() {
        val failing = activeVm("vm-aaaaaaaaaaaa")
        val missing = activeVm("vm-bbbbbbbbbbbb")
        every { client.getState(failing.id) } throws BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE)
        every { client.getState(missing.id) } returns null

        assertEquals(VmLiveStatus.UNKNOWN, reader.read(failing))
        assertEquals(VmLiveStatus.UNKNOWN, reader.read(missing))
    }

    @Test
    fun `만드는 중이거나 만들기에 실패한 VM은 묻지 않고 unknown이다`() {
        val creating = vm("vm-aaaaaaaaaaaa")
        val failed = vm("vm-bbbbbbbbbbbb").also { it.markFailed() }

        assertEquals(VmLiveStatus.UNKNOWN, reader.read(creating))
        assertEquals(VmLiveStatus.UNKNOWN, reader.read(failed))
        verify(exactly = 0) { client.getState(any()) }
    }

    private fun activeVm(id: String = "vm-k3m9x2p7q4ta") = vm(id).also { it.markActive("192.0.2.10") }

    private fun vm(id: String) =
        Vm.create(
            id = id,
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
        val RUNNING = VmLiveState(VmPower.RUNNING, cpuPercent = 12.5, memoryPercent = 40.0, uptimeSec = 60)
        val STOPPED = VmLiveState(VmPower.STOPPED, cpuPercent = 0.0, memoryPercent = 0.0, uptimeSec = 0)
    }
}
