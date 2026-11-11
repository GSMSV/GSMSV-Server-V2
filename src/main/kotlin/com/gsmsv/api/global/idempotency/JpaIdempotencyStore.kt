package com.gsmsv.api.global.idempotency

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class JpaIdempotencyStore(
    private val repository: IdempotencyRecordRepository,
) : IdempotencyStore {
    override fun begin(
        userId: String,
        key: String,
        fingerprint: String,
        now: Instant,
    ): BeginResult {
        repository.deleteExpiredByUserAndKey(userId, key, now)
        val inserted =
            repository.insertIfAbsent(UUID.randomUUID().toString(), userId, key, fingerprint, now, now.plus(RETENTION))
        if (inserted == 1) return BeginResult.Proceed

        // 동시에 지워졌으면 잡을 수 없다. 처리 중으로 보고 클라이언트가 다시 시도하게 한다
        val record = repository.findByUserIdAndIdempotencyKey(userId, key) ?: return BeginResult.InProgress
        val status = record.responseStatus
        return when {
            record.fingerprint != fingerprint -> BeginResult.Reused
            status == null -> BeginResult.InProgress
            else -> BeginResult.Replay(StoredResponse(status, record.contentType, record.responseBody ?: ByteArray(0)))
        }
    }

    override fun complete(
        userId: String,
        key: String,
        response: StoredResponse,
    ) {
        repository.saveResponse(userId, key, response.status, response.contentType, response.body)
    }

    override fun abandon(
        userId: String,
        key: String,
    ) {
        repository.deleteByUserAndKey(userId, key)
    }

    override fun deleteExpired(now: Instant): Int = repository.deleteAllExpired(now)

    companion object {
        /** 같은 키의 응답을 다시 보내 주는 기간 */
        val RETENTION: Duration = Duration.ofHours(24)
    }
}
