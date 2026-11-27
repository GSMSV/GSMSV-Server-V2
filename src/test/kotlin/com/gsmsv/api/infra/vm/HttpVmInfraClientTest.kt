package com.gsmsv.api.infra.vm

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.infra.job.InfraJobAccepted
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.io.IOException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class HttpVmInfraClientTest {
    private val builder =
        RestClient
            .builder()
            .baseUrl("http://go.test")
            .defaultHeader("X-Internal-Secret", "test-secret")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = HttpVmInfraClient(builder.build())

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
    fun `VM 만들기는 멱등 키와 함께 POST로 보내고 작업을 돌려준다`() {
        server
            .expect(requestTo("http://go.test/vms"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Internal-Secret", "test-secret"))
            .andExpect(header("Idempotency-Key", OPERATION_ID))
            .andExpect(
                content().json(
                    """
                    {"id":"$VM_ID","node":"node-a","vmid":101,"image":"ubuntu-24.04","vcpu":2,"memory_mb":4096,
                     "ssd_gb":20,"internal_ip":"192.0.2.23",
                     "cloud_init":{"user":"ubuntu","password":"example-password-1","hostname":"web"}}
                    """.trimIndent(),
                    true,
                ),
            ).andRespond(
                withStatus(HttpStatus.ACCEPTED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"job_id":"job-5f6g7h8j9k2m","status":"queued","queue_position":1}"""),
            )

        val accepted = client.createVm(request, OPERATION_ID)

        assertEquals(InfraJobAccepted("job-5f6g7h8j9k2m", "queued", 1), accepted)
        server.verify()
    }

    @Test
    fun `VM 지우기는 멱등 키와 함께 DELETE로 보낸다`() {
        server
            .expect(requestTo("http://go.test/vms/$VM_ID"))
            .andExpect(method(HttpMethod.DELETE))
            .andExpect(header("Idempotency-Key", OPERATION_ID))
            .andRespond(
                withStatus(HttpStatus.ACCEPTED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"job_id":"job-8q4n6c3v2k7m","status":"queued","queue_position":0}"""),
            )

        val accepted = client.deleteVm(VM_ID, OPERATION_ID)

        assertEquals("job-8q4n6c3v2k7m", accepted?.jobId)
        server.verify()
    }

    @Test
    fun `지울 VM이 Infra에 없으면 null을 돌려준다`() {
        server.expect(requestTo("http://go.test/vms/$VM_ID")).andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertNull(client.deleteVm(VM_ID, OPERATION_ID))
    }

    @Test
    fun `같은 VM에 끝나지 않은 작업이 있어 Go가 409를 주면 409 CONFLICT로 바꾼다`() {
        server
            .expect(requestTo("http://go.test/vms/$VM_ID"))
            .andRespond(
                withStatus(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"code":"INVALID_STATE","retryable":false,"request_id":"r"}"""),
            )

        val e = assertFailsWith<BusinessException> { client.deleteVm(VM_ID, OPERATION_ID) }

        assertEquals(ErrorCode.CONFLICT, e.errorCode)
    }

    @Test
    fun `VM 실제 상태를 읽는다`() {
        server
            .expect(requestTo("http://go.test/vms/$VM_ID"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """
                    {"id":"$VM_ID","node":"node-a","vmid":101,"power":"stopped","vcpu":2,"memory_mb":4096,
                     "ssd_gb":20,"observed_at":"2026-11-14T09:00:05Z","extra":"무시"}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val state = client.getVm(VM_ID)

        assertEquals(
            InfraVmState(VM_ID, "node-a", 101, VmPower.STOPPED, 2, 4096, 20, Instant.parse("2026-11-14T09:00:05Z")),
            state,
        )
    }

    @Test
    fun `VM이 Infra에 없으면 실제 상태는 null이다`() {
        server.expect(requestTo("http://go.test/vms/$VM_ID")).andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertNull(client.getVm(VM_ID))
    }

    @Test
    fun `Go가 422나 500을 주면 502 GATEWAY_SYNC_FAILED로 바꾼다`() {
        listOf(HttpStatus.UNPROCESSABLE_CONTENT, HttpStatus.INTERNAL_SERVER_ERROR).forEach { status ->
            server.reset()
            server.expect(requestTo("http://go.test/vms")).andRespond(withStatus(status))

            val e = assertFailsWith<BusinessException> { client.createVm(request, OPERATION_ID) }

            assertEquals(ErrorCode.GATEWAY_SYNC_FAILED, e.errorCode)
        }
    }

    @Test
    fun `연결 실패면 503 UPSTREAM_UNAVAILABLE로 바꾼다`() {
        server
            .expect(requestTo("http://go.test/vms"))
            .andRespond { throw ResourceAccessException("연결 실패", IOException("refused")) }

        val e = assertFailsWith<BusinessException> { client.createVm(request, OPERATION_ID) }

        assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, e.errorCode)
    }

    @Test
    fun `요청의 toString에 초기 비밀번호가 나오지 않는다`() {
        assertFalse(request.toString().contains("example-password-1"))
    }

    private companion object {
        const val VM_ID = "vm-k3m9x2p7q4ta"
        const val OPERATION_ID = "op-7w8k9q0p1a2s"
    }
}
