package com.gsmsv.api.global.idempotency

import java.time.Instant

/** 처음 처리한 요청의 응답. 같은 키로 다시 오면 이 응답을 그대로 돌려준다 */
class StoredResponse(
    val status: Int,
    val contentType: String?,
    val body: ByteArray,
)

sealed interface BeginResult {
    /** 처음 보는 키. 요청을 처리하고 [IdempotencyStore.complete] 또는 [IdempotencyStore.abandon]을 부른다 */
    data object Proceed : BeginResult

    /** 같은 키·같은 요청이 이미 끝났다 */
    class Replay(
        val response: StoredResponse,
    ) : BeginResult

    /** 같은 키에 다른 요청이 왔다 */
    data object Reused : BeginResult

    /** 같은 요청이 아직 처리 중이다 */
    data object InProgress : BeginResult
}

interface IdempotencyStore {
    fun begin(
        userId: String,
        key: String,
        fingerprint: String,
        now: Instant,
    ): BeginResult

    fun complete(
        userId: String,
        key: String,
        response: StoredResponse,
    )

    /** 처리에 실패해 응답을 남기지 않을 때. 같은 키로 다시 시도할 수 있게 지운다 */
    fun abandon(
        userId: String,
        key: String,
    )

    fun deleteExpired(now: Instant): Int
}
