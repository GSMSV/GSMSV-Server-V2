package com.gsmsv.api.global.response

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/**
 * 목록 조회의 쿼리 파라미터(`cursor`, `limit`). controller에서 `@Valid @ModelAttribute`로 받는다.
 * `limit`이 범위를 벗어나면 400 `VALIDATION_FAILED`.
 */
data class CursorPageRequest(
    val cursor: String? = null,
    @field:Min(1)
    @field:Max(MAX_LIMIT.toLong())
    val limit: Int = DEFAULT_LIMIT,
) {
    /** 첫 페이지면 null. 형식이 틀리면 400 `INVALID_CURSOR`. */
    fun decodedCursor(): Cursor? = cursor?.let(Cursor::decode)

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 100
    }
}
