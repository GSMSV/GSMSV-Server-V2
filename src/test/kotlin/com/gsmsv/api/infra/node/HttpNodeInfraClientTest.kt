package com.gsmsv.api.infra.node

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpNodeInfraClientTest {
    private val builder = RestClient.builder().baseUrl("http://go.test")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = HttpNodeInfraClient(builder.build())

    @Test
    fun `노드 현황을 읽고 닿지 않는 노드는 총량 없이 돌려준다`() {
        server
            .expect(requestTo("http://go.test/nodes?refresh=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """
                    {"items":[
                      {"node":"node-a","online":true,"observed_at":"2026-11-12T09:00:05Z",
                       "total":{"cpu_cores":16,"cpu_threads":32,"memory_mb":131072,"ssd_gb":1800},
                       "usage":{"cpu_percent":23.5,"memory_used_mb":61440,"uptime_sec":1209600},
                       "configured":{"vcpu":40,"memory_mb":98304,"ssd_gb":1100},
                       "clone_queue_length":2},
                      {"node":"node-b","online":false,"observed_at":"2026-11-12T09:00:05Z",
                       "total":null,"usage":null,"configured":null,"clone_queue_length":0}
                    ]}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val nodes = client.listNodes(refresh = true)

        val observedAt = Instant.parse("2026-11-12T09:00:05Z")
        assertEquals(
            listOf(
                InfraNodeStatus(
                    node = "node-a",
                    online = true,
                    observedAt = observedAt,
                    total = InfraNodeTotal(16, 32, 131072, 1800),
                    usage = InfraNodeUsage(23.5, 61440, 1209600),
                    configured = InfraNodeConfigured(40, 98304, 1100),
                    cloneQueueLength = 2,
                ),
                InfraNodeStatus(node = "node-b", online = false, observedAt = observedAt, cloneQueueLength = 0),
            ),
            nodes,
        )
        server.verify()
    }

    @Test
    fun `refresh를 주지 않으면 false로 보낸다`() {
        server
            .expect(requestTo("http://go.test/nodes?refresh=false"))
            .andRespond(withSuccess("""{"items":[]}""", MediaType.APPLICATION_JSON))

        assertEquals(emptyList(), client.listNodes())
    }

    @Test
    fun `Go가 504를 주면 503 UPSTREAM_UNAVAILABLE로 바꾼다`() {
        server.expect(requestTo("http://go.test/nodes?refresh=false")).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT))

        val e = assertFailsWith<BusinessException> { client.listNodes() }

        assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, e.errorCode)
    }

    @Test
    fun `Go가 404를 주면 502 GATEWAY_SYNC_FAILED로 바꾼다`() {
        server.expect(requestTo("http://go.test/nodes?refresh=false")).andRespond(withStatus(HttpStatus.NOT_FOUND))

        val e = assertFailsWith<BusinessException> { client.listNodes() }

        assertEquals(ErrorCode.GATEWAY_SYNC_FAILED, e.errorCode)
    }
}
