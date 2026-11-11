package com.gsmsv.api.global.idempotency

import com.gsmsv.api.global.authorization.Authenticated
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.support.ApiWebMvcTest
import com.gsmsv.api.support.TestTokens
import com.gsmsv.api.support.bearer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ApiWebMvcTest(IdempotencyFilterTest.TestController::class)
@Import(IdempotencyFilterTest.TestController::class, IdempotencyFilterTest.StoreConfig::class)
class IdempotencyFilterTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokens: TestTokens,
    @Autowired private val store: InMemoryIdempotencyStore,
) {
    @RestController
    class TestController {
        val calls = AtomicInteger()

        @Authenticated
        @PostMapping("/test/create")
        @ResponseStatus(HttpStatus.CREATED)
        fun create(
            @CurrentUser user: AuthenticatedUser,
            @RequestBody body: Map<String, String>,
        ): Map<String, Any> = mapOf("call" to calls.incrementAndGet(), "name" to (body["name"] ?: ""))

        @Authenticated
        @PostMapping("/test/fail")
        fun fail(): Map<String, String> = throw IllegalStateException("boom")
    }

    class StoreConfig {
        @Bean
        fun store() = InMemoryIdempotencyStore()
    }

    /** 만료 시각 관리까지 JPA 저장소와 똑같이 흉내 내지 않는다. 필터가 저장소 결과에 맞게 움직이는지만 본다 */
    class InMemoryIdempotencyStore : IdempotencyStore {
        private data class Entry(
            val fingerprint: String,
            var response: StoredResponse? = null,
        )

        private val entries = ConcurrentHashMap<Pair<String, String>, Entry>()

        override fun begin(
            userId: String,
            key: String,
            fingerprint: String,
            now: Instant,
        ): BeginResult {
            if (alwaysInProgress) return BeginResult.InProgress
            val created = Entry(fingerprint)
            val existing = entries.putIfAbsent(userId to key, created) ?: return BeginResult.Proceed
            val response = existing.response
            return when {
                existing.fingerprint != fingerprint -> BeginResult.Reused
                response == null -> BeginResult.InProgress
                else -> BeginResult.Replay(response)
            }
        }

        override fun complete(
            userId: String,
            key: String,
            response: StoredResponse,
        ) {
            entries[userId to key]?.response = response
        }

        override fun abandon(
            userId: String,
            key: String,
        ) {
            entries.remove(userId to key)
        }

        override fun deleteExpired(now: Instant): Int = 0

        var alwaysInProgress = false

        fun clear() {
            entries.clear()
            alwaysInProgress = false
        }

        fun contains(
            userId: String,
            key: String,
        ) = entries.containsKey(userId to key)
    }

    @Autowired
    private lateinit var controller: TestController

    @BeforeEach
    fun reset() {
        store.clear()
        controller.calls.set(0)
    }

    private fun create(
        userId: String = ME,
        key: String? = "key-1",
        json: String = """{"name":"a"}""",
        path: String = "/test/create",
    ) = mockMvc.post(path) {
        bearer(tokens.user(userId))
        key?.let { header("Idempotency-Key", it) }
        contentType = MediaType.APPLICATION_JSON
        content = json
    }

    @Test
    fun `같은 키로 다시 보내면 처음 응답을 그대로 받고 다시 처리하지 않는다`() {
        create().andReturn().response.let {
            assertEquals(201, it.status)
            assertEquals("""{"call":1,"name":"a"}""", it.contentAsString)
        }

        create().andReturn().response.let {
            assertEquals(201, it.status)
            assertEquals("""{"call":1,"name":"a"}""", it.contentAsString)
            assertEquals("true", it.getHeader("Idempotent-Replayed"))
        }
        assertEquals(1, controller.calls.get())
    }

    @Test
    fun `같은 키에 본문이 다르면 422 IDEMPOTENCY_KEY_REUSED를 돌려준다`() {
        create()

        create(json = """{"name":"b"}""").andReturn().response.let {
            assertEquals(422, it.status)
            assertTrue(it.contentAsString.contains("IDEMPOTENCY_KEY_REUSED"))
        }
        assertEquals(1, controller.calls.get())
    }

    @Test
    fun `같은 키에 경로가 다르면 422를 돌려준다`() {
        create()

        assertEquals(422, create(path = "/test/fail").andReturn().response.status)
    }

    @Test
    fun `키가 없으면 매번 처리한다`() {
        create(key = null)
        create(key = null)

        assertEquals(2, controller.calls.get())
    }

    @Test
    fun `다른 사용자는 같은 키를 따로 쓴다`() {
        create(userId = ME)
        create(userId = OTHER).andReturn().response.let {
            assertEquals(201, it.status)
            assertEquals("""{"call":2,"name":"a"}""", it.contentAsString)
        }
    }

    @Test
    fun `같은 키의 요청이 처리 중이면 409 IDEMPOTENCY_KEY_IN_PROGRESS를 돌려준다`() {
        store.alwaysInProgress = true

        create().andReturn().response.let {
            assertEquals(409, it.status)
            assertTrue(it.contentAsString.contains("IDEMPOTENCY_KEY_IN_PROGRESS"))
        }
        assertEquals(0, controller.calls.get())
    }

    @Test
    fun `5xx 응답은 저장하지 않아 같은 키로 다시 시도할 수 있다`() {
        create(path = "/test/fail").andReturn().response.let { assertEquals(500, it.status) }

        assertTrue(!store.contains(ME, "key-1"))
    }

    @Test
    fun `형식이 틀린 키는 400 VALIDATION_FAILED를 돌려준다`() {
        create(key = "공백 포함").andReturn().response.let {
            assertEquals(400, it.status)
            assertTrue(it.contentAsString.contains("VALIDATION_FAILED"))
        }
        assertEquals(0, controller.calls.get())
    }

    @Test
    fun `인증되지 않은 요청은 키가 있어도 401이고 저장하지 않는다`() {
        mockMvc
            .post("/test/create") {
                header("Idempotency-Key", "key-1")
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"a"}"""
            }.andReturn()
            .response
            .let { assertEquals(401, it.status) }

        assertTrue(!store.contains(ME, "key-1"))
    }

    companion object {
        private const val ME = "user-4p8w2k9m1xza"
        private const val OTHER = "user-7c3d5f8g2hjk"
    }
}
