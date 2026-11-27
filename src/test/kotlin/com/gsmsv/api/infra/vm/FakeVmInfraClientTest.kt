package com.gsmsv.api.infra.vm

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.infra.job.FakeJobInfraClient
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class FakeVmInfraClientTest {
    private val jobs = FakeJobInfraClient()
    private val client = FakeVmInfraClient(jobs)

    private val request =
        InfraVmCreateRequest(
            id = VM_ID,
            node = "node-a",
            vmid = 101,
            image = "ubuntu-24.04",
            vcpu = 2,
            memoryMb = 4096,
            ssdGb = 20,
            internalIp = "192.0.2.23",
            cloudInit = InfraVmCloudInit(user = "ubuntu", password = "example-password-1", hostname = "web"),
        )

    @Test
    fun `만들기 요청을 기억하고 끝난 작업을 넣어 둔다`() {
        val accepted = client.createVm(request, "op-create")

        assertEquals(listOf(FakeVmInfraClient.CreateCall(request, "op-create")), client.createCalls)
        val job = assertNotNull(jobs.getJob(accepted.jobId))
        assertEquals("vm.create", job.type)
        assertEquals("succeeded", job.status)
        assertEquals(VmPower.RUNNING, client.getVm(VM_ID)?.power)
    }

    @Test
    fun `같은 멱등 키로 다시 부르면 처음 작업을 다시 준다`() {
        val first = client.createVm(request, "op-create")
        val second = client.createVm(request, "op-create")

        assertSame(first, second)
        assertEquals(1, client.createCalls.size)
    }

    @Test
    fun `이미 있는 VM을 다른 키로 만들면 409 CONFLICT다`() {
        client.createVm(request, "op-create")

        val e = assertFailsWith<BusinessException> { client.createVm(request, "op-other") }

        assertEquals(ErrorCode.CONFLICT, e.errorCode)
    }

    @Test
    fun `지우면 VM이 없어지고 없는 VM을 지우면 null이다`() {
        client.createVm(request, "op-create")

        assertNotNull(client.deleteVm(VM_ID, "op-delete"))
        assertNull(client.getVm(VM_ID))
        assertNull(client.deleteVm(VM_ID, "op-delete-again"))
        assertEquals(2, client.deleteCalls.size)
    }

    @Test
    fun `failWith를 지정하면 그 오류로 실패한다`() {
        client.failWith(BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE))

        val e = assertFailsWith<BusinessException> { client.createVm(request, "op-create") }

        assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, e.errorCode)
    }

    private companion object {
        const val VM_ID = "vm-k3m9x2p7q4ta"
    }
}
