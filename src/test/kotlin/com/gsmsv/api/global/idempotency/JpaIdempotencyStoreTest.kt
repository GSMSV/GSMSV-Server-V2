package com.gsmsv.api.global.idempotency

import com.gsmsv.api.TestcontainersConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="])
@Tag("integration")
@Transactional
class JpaIdempotencyStoreTest
    @Autowired
    constructor(
        private val store: JpaIdempotencyStore,
    ) {
        private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        @Test
        fun `처음 보는 키는 처리하고 끝나면 같은 응답을 돌려준다`() {
            assertEquals(BeginResult.Proceed, store.begin(USER, "k1", "fp", now))
            assertEquals(BeginResult.InProgress, store.begin(USER, "k1", "fp", now))

            store.complete(USER, "k1", StoredResponse(202, "application/json", """{"a":1}""".toByteArray()))

            val replay = assertIs<BeginResult.Replay>(store.begin(USER, "k1", "fp", now))
            assertEquals(202, replay.response.status)
            assertEquals("application/json", replay.response.contentType)
            assertContentEquals("""{"a":1}""".toByteArray(), replay.response.body)
        }

        @Test
        fun `같은 키에 지문이 다르면 재사용으로 본다`() {
            store.begin(USER, "k1", "fp", now)

            assertEquals(BeginResult.Reused, store.begin(USER, "k1", "other", now))
        }

        @Test
        fun `사용자가 다르면 같은 키를 따로 쓴다`() {
            store.begin(USER, "k1", "fp", now)

            assertEquals(BeginResult.Proceed, store.begin("user-7c3d5f8g2hjk", "k1", "fp", now))
        }

        @Test
        fun `포기하면 같은 키로 다시 시작할 수 있다`() {
            store.begin(USER, "k1", "fp", now)
            store.abandon(USER, "k1")

            assertEquals(BeginResult.Proceed, store.begin(USER, "k1", "fp", now))
        }

        @Test
        fun `24시간이 지난 키는 새 키로 다시 시작한다`() {
            store.begin(USER, "k1", "fp", now)
            store.complete(USER, "k1", StoredResponse(201, null, ByteArray(0)))

            assertEquals(BeginResult.Proceed, store.begin(USER, "k1", "other", now.plus(Duration.ofHours(24))))
        }

        @Test
        fun `만료된 키만 정리한다`() {
            store.begin(USER, "old", "fp", now.minus(Duration.ofHours(25)))
            store.begin(USER, "new", "fp", now)

            assertEquals(1, store.deleteExpired(now))
            assertEquals(BeginResult.InProgress, store.begin(USER, "new", "fp", now))
        }

        companion object {
            private const val USER = "user-4p8w2k9m1xza"
        }
    }
