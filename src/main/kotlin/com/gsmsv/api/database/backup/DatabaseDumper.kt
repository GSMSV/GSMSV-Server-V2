package com.gsmsv.api.database.backup

import java.io.InputStream
import java.io.OutputStream

/** 공유 DB 서버의 database 하나를 SQL 덤프로 내보내고 되돌린다. gzip 압축은 부르는 쪽이 한다 */
interface DatabaseDumper {
    fun dump(
        databaseId: String,
        output: OutputStream,
    )

    /** [input]의 SQL 덤프로 database를 덮어쓴다 (7.12) */
    fun restore(
        databaseId: String,
        input: InputStream,
    )
}

class DatabaseDumpException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
