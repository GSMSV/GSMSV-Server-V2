package com.gsmsv.api.global.idempotency

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface IdempotencyRecordRepository : JpaRepository<IdempotencyRecord, String> {
    fun findByUserIdAndIdempotencyKey(
        userId: String,
        idempotencyKey: String,
    ): IdempotencyRecord?

    /** 같은 (사용자, 키)가 이미 있으면 아무것도 넣지 않고 0을 돌려준다. 동시에 온 요청 중 하나만 1을 받는다 */
    @Modifying
    @Query(
        value = """
            insert into idempotency_keys (id, user_id, idempotency_key, fingerprint, created_at, expires_at)
            values (:id, :userId, :key, :fingerprint, :createdAt, :expiresAt)
            on conflict (user_id, idempotency_key) do nothing
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("id") id: String,
        @Param("userId") userId: String,
        @Param("key") key: String,
        @Param("fingerprint") fingerprint: String,
        @Param("createdAt") createdAt: Instant,
        @Param("expiresAt") expiresAt: Instant,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update IdempotencyRecord r
        set r.responseStatus = :status, r.contentType = :contentType, r.responseBody = :body
        where r.userId = :userId and r.idempotencyKey = :key
        """,
    )
    fun saveResponse(
        @Param("userId") userId: String,
        @Param("key") key: String,
        @Param("status") status: Int,
        @Param("contentType") contentType: String?,
        @Param("body") body: ByteArray,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from IdempotencyRecord r where r.userId = :userId and r.idempotencyKey = :key")
    fun deleteByUserAndKey(
        @Param("userId") userId: String,
        @Param("key") key: String,
    ): Int

    /** 만료된 키 하나를 지운다. 새 요청이 같은 키를 다시 쓸 수 있게 한다 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "delete from IdempotencyRecord r where r.userId = :userId and r.idempotencyKey = :key and r.expiresAt <= :now",
    )
    fun deleteExpiredByUserAndKey(
        @Param("userId") userId: String,
        @Param("key") key: String,
        @Param("now") now: Instant,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from IdempotencyRecord r where r.expiresAt <= :now")
    fun deleteAllExpired(
        @Param("now") now: Instant,
    ): Int
}
