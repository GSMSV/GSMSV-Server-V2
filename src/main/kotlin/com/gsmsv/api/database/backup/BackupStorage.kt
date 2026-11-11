package com.gsmsv.api.database.backup

import java.io.InputStream
import java.io.OutputStream

/**
 * 백업 파일 저장소. 저장 위치(로컬 디스크, 오브젝트 스토리지 등)는 구현이 정한다.
 * 위치가 정해지면 구현만 바꾼다 (24번 DBaaS 7.11, 위치 미정).
 */
interface BackupStorage {
    /** [key]에 쓴다. [writer]가 실패하면 반쯤 쓴 파일을 남기지 않는다. 쓴 바이트 수를 돌려준다 */
    fun write(
        key: String,
        writer: (OutputStream) -> Unit,
    ): Long

    fun read(key: String): InputStream

    /** 이미 없어도 오류가 아니다 */
    fun delete(key: String)
}
