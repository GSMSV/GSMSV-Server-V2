package com.gsmsv.api.infra.network

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpNetworkInfraClientTest {
    private val builder =
        RestClient
            .builder()
            .baseUrl("http://go.test")
            .defaultHeader("X-Internal-Secret", "test-secret")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = HttpNetworkInfraClient(builder.build())

    private val rule = VmPortRule("port-abcd1234", 20022, 22, PortProtocol.TCP, "10.0.0.0/24")

    @Test
    fun `포트 집합 전체를 PUT으로 보내고 결과를 돌려준다`() {
        server
            .expect(requestTo("http://go.test/network/vms/vm-abcd1234/ports"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header("X-Internal-Secret", "test-secret"))
            .andExpect(jsonPath("$.internal_ip").value("192.0.2.10"))
            .andExpect(jsonPath("$.ports.length()").value(1))
            .andExpect(jsonPath("$.ports[0].id").value("port-abcd1234"))
            .andExpect(jsonPath("$.ports[0].external").value(20022))
            .andExpect(jsonPath("$.ports[0].internal").value(22))
            .andExpect(jsonPath("$.ports[0].protocol").value("tcp"))
            .andExpect(jsonPath("$.ports[0].allowed_source").value("10.0.0.0/24"))
            .andRespond(withSuccess("""{"applied":true,"added":1,"removed":0,"extra":"무시"}""", MediaType.APPLICATION_JSON))

        val result = client.syncVmPorts("vm-abcd1234", "192.0.2.10", listOf(rule))

        assertEquals(PortSyncResult(applied = true, added = 1, removed = 0), result)
        server.verify()
    }

    @Test
    fun `빈 포트 집합이면 빈 배열을 보낸다`() {
        server
            .expect(requestTo("http://go.test/network/vms/vm-abcd1234/ports"))
            .andExpect(jsonPath("$.ports.length()").value(0))
            .andRespond(withSuccess("""{"applied":true,"added":0,"removed":2}""", MediaType.APPLICATION_JSON))

        val result = client.syncVmPorts("vm-abcd1234", "192.0.2.10", emptyList())

        assertEquals(2, result.removed)
    }

    @Test
    fun `HTTPS 라우트 전체 목록을 PUT으로 보낸다`() {
        server
            .expect(requestTo("http://go.test/https/routes"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header("X-Internal-Secret", "test-secret"))
            .andExpect(content().json("""{"routes":[{"id":"dom-abcd1234","host":"a.example.test","upstream":"192.0.2.10:8080"}]}"""))
            .andRespond(withSuccess("""{"applied":true}""", MediaType.APPLICATION_JSON))

        val result = client.syncHttpsRoutes(listOf(HttpsRoute("dom-abcd1234", "a.example.test", "192.0.2.10:8080")))

        assertEquals(RouteSyncResult(applied = true), result)
    }

    @Test
    fun `applied가 false면 502 GATEWAY_SYNC_FAILED로 바꾼다`() {
        server
            .expect(requestTo("http://go.test/https/routes"))
            .andRespond(withSuccess("""{"applied":false}""", MediaType.APPLICATION_JSON))

        val e = assertFailsWith<BusinessException> { client.syncHttpsRoutes(emptyList()) }

        assertEquals(ErrorCode.GATEWAY_SYNC_FAILED, e.errorCode)
    }

    @Test
    fun `Go가 오류 응답을 주면 502 GATEWAY_SYNC_FAILED로 바꾸고 본문을 노출하지 않는다`() {
        server
            .expect(requestTo("http://go.test/network/vms/vm-abcd1234/ports"))
            .andRespond(
                withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"code":"X","detail":{"host":"10.9.9.9"}}"""),
            )

        val e = assertFailsWith<BusinessException> { client.syncVmPorts("vm-abcd1234", "192.0.2.10", listOf(rule)) }

        assertEquals(ErrorCode.GATEWAY_SYNC_FAILED, e.errorCode)
        assertEquals(ErrorCode.GATEWAY_SYNC_FAILED.message, e.message)
    }

    @Test
    fun `Go가 503이나 504를 주면 503 UPSTREAM_UNAVAILABLE로 바꾼다`() {
        listOf(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT).forEach { status ->
            server.reset()
            server.expect(requestTo("http://go.test/https/routes")).andRespond(withStatus(status))

            val e = assertFailsWith<BusinessException> { client.syncHttpsRoutes(emptyList()) }

            assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, e.errorCode)
        }
    }

    @Test
    fun `연결 실패면 503 UPSTREAM_UNAVAILABLE로 바꾼다`() {
        server
            .expect(requestTo("http://go.test/https/routes"))
            .andRespond { throw ResourceAccessException("연결 실패", IOException("refused")) }

        val e = assertFailsWith<BusinessException> { client.syncHttpsRoutes(emptyList()) }

        assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, e.errorCode)
    }

    @Test
    fun `응답 본문을 읽을 수 없으면 502 GATEWAY_SYNC_FAILED로 바꾼다`() {
        server
            .expect(requestTo("http://go.test/https/routes"))
            .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON))

        val e = assertFailsWith<BusinessException> { client.syncHttpsRoutes(emptyList()) }

        assertEquals(ErrorCode.GATEWAY_SYNC_FAILED, e.errorCode)
    }
}
