package com.gsmsv.api.global.response

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64

/**
 * 목록의 다음 페이지 위치. 목록은 `created_at` 내림차순, 같으면 `id` 내림차순으로 정렬한다고 보고
 * 마지막 항목의 두 값을 담는다.
 *
 * 클라이언트에는 내용을 알 수 없는 문자열로 준다. 형식을 바꿔도 API가 깨지지 않게 하려는 것이다.
 */
data class Cursor(
    val createdAt: Instant,
    val id: String,
) {
    fun encode(): String = ENCODER.encodeToString("$createdAt$SEPARATOR$id".toByteArray(Charsets.UTF_8))

    companion object {
        private const val SEPARATOR = '|'
        private val ID_PATTERN = Regex("^[a-z0-9-]{1,64}$")
        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()

        /** 클라이언트가 보낸 커서를 읽는다. 형식이 틀리면 400 `INVALID_CURSOR`. */
        fun decode(value: String): Cursor {
            val decoded =
                try {
                    String(DECODER.decode(value), Charsets.UTF_8)
                } catch (_: IllegalArgumentException) {
                    throw invalid()
                }
            val parts = decoded.split(SEPARATOR)
            if (parts.size != 2 || !ID_PATTERN.matches(parts[1])) throw invalid()
            val createdAt =
                try {
                    Instant.parse(parts[0])
                } catch (_: DateTimeParseException) {
                    throw invalid()
                }
            return Cursor(createdAt, parts[1])
        }

        private fun invalid() = BusinessException(ErrorCode.INVALID_CURSOR)
    }
}
