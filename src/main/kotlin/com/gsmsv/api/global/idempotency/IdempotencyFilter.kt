package com.gsmsv.api.global.idempotency

import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.SecurityErrorResponder
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat

/**
 * `Idempotency-Key` 헤더가 있는 쓰기 요청(POST·PUT·PATCH·DELETE)을 한 번만 처리한다.
 *
 * - 처음 보는 키: 요청을 처리하고 응답을 24시간 저장한다
 * - 같은 키·같은 요청: 처리하지 않고 저장한 응답을 그대로 돌려준다 (`Idempotent-Replayed: true`)
 * - 같은 키·다른 요청(메서드·경로·본문이 다름): 422 `IDEMPOTENCY_KEY_REUSED`
 * - 같은 키의 요청이 아직 처리 중: 409 `IDEMPOTENCY_KEY_IN_PROGRESS`
 *
 * 키는 요청자별로 따로 본다. 인증 필터 뒤에서 돌아야 요청자를 알 수 있다. 인증되지 않은 요청은 건드리지 않고 넘긴다.
 * 5xx와 인증·권한 오류(401·403)·429는 저장하지 않는다. 처리되지 않은 요청이라 같은 키로 다시 시도할 수 있어야 한다.
 */
class IdempotencyFilter(
    private val store: IdempotencyStore,
    private val errorResponder: SecurityErrorResponder,
    private val clock: () -> Instant = Instant::now,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method !in WRITE_METHODS || request.getHeader(HEADER) == null

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val user = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
        if (user == null) {
            filterChain.doFilter(request, response)
            return
        }
        val key = request.getHeader(HEADER)
        if (!VALID_KEY.matches(key)) {
            errorResponder.respond(request, response, ErrorCode.VALIDATION_FAILED)
            return
        }
        val body = request.inputStream.readNBytes(MAX_BODY_BYTES + 1)
        if (body.size > MAX_BODY_BYTES) {
            errorResponder.respond(request, response, ErrorCode.INVALID_REQUEST)
            return
        }

        when (val result = store.begin(user.userId, key, fingerprint(request, body), clock())) {
            BeginResult.Proceed -> process(user.userId, key, CachedBodyRequest(request, body), response, filterChain)
            is BeginResult.Replay -> replay(result.response, response)
            BeginResult.Reused -> errorResponder.respond(request, response, ErrorCode.IDEMPOTENCY_KEY_REUSED)
            BeginResult.InProgress -> errorResponder.respond(request, response, ErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS)
        }
    }

    private fun process(
        userId: String,
        key: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val wrapped = ContentCachingResponseWrapper(response)
        try {
            filterChain.doFilter(request, wrapped)
            if (wrapped.status in UNSTORED_STATUS || wrapped.status >= 500) {
                store.abandon(userId, key)
            } else {
                store.complete(userId, key, StoredResponse(wrapped.status, wrapped.contentType, wrapped.contentAsByteArray))
            }
        } catch (e: Throwable) {
            store.abandon(userId, key)
            throw e
        }
        wrapped.copyBodyToResponse()
    }

    private fun replay(
        stored: StoredResponse,
        response: HttpServletResponse,
    ) {
        response.status = stored.status
        stored.contentType?.let { response.contentType = it }
        response.setHeader(REPLAYED_HEADER, "true")
        response.setContentLength(stored.body.size)
        response.outputStream.write(stored.body)
    }

    private fun fingerprint(
        request: HttpServletRequest,
        body: ByteArray,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        // 구분용 0 바이트로 "ab"+"c"와 "a"+"bc"가 같아지지 않게 한다
        listOf(request.method, request.requestURI, request.queryString.orEmpty()).forEach {
            digest.update(it.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        digest.update(body)
        return HexFormat.of().formatHex(digest.digest())
    }

    /** 본문을 한 번 읽은 뒤에도 controller가 다시 읽을 수 있게 한다 */
    private class CachedBodyRequest(
        request: HttpServletRequest,
        private val body: ByteArray,
    ) : HttpServletRequestWrapper(request) {
        override fun getInputStream(): ServletInputStream {
            val source = ByteArrayInputStream(body)
            return object : ServletInputStream() {
                override fun read(): Int = source.read()

                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int = source.read(b, off, len)

                override fun isFinished(): Boolean = source.available() == 0

                override fun isReady(): Boolean = true

                override fun setReadListener(listener: ReadListener?) = throw UnsupportedOperationException()
            }
        }

        override fun getReader(): BufferedReader =
            BufferedReader(InputStreamReader(inputStream, characterEncoding ?: Charsets.UTF_8.name()))
    }

    companion object {
        const val HEADER = "Idempotency-Key"
        const val REPLAYED_HEADER = "Idempotent-Replayed"
        const val MAX_BODY_BYTES = 1024 * 1024

        private val WRITE_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
        private val UNSTORED_STATUS = setOf(401, 403, 429)

        /** 보이는 ASCII만 255자까지. DB 열 길이와 맞춘다 */
        private val VALID_KEY = Regex("^[\\x21-\\x7E]{1,255}$")
    }
}
