package com.gsmsv.api.global.response

/** 커서 방식 목록 응답. 마지막 페이지면 [nextCursor]가 null이다. */
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String?,
) {
    companion object {
        /**
         * repository가 `limit + 1`개까지 읽어 온 [rows]로 페이지를 만든다.
         * 한 개를 더 읽어서 다음 페이지가 있는지 판단하므로, 빈 페이지를 한 번 더 요청하게 만들지 않는다.
         */
        fun <E, T> of(
            rows: List<E>,
            limit: Int,
            cursorOf: (E) -> Cursor,
            transform: (E) -> T,
        ): CursorPage<T> {
            val pageRows = rows.take(limit)
            val nextCursor = if (rows.size > limit) cursorOf(pageRows.last()).encode() else null
            return CursorPage(pageRows.map(transform), nextCursor)
        }
    }
}
