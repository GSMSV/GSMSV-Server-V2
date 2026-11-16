package com.gsmsv.api.database.backup

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class LocalFileBackupStorageTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `쓴 내용을 그대로 읽고 크기를 돌려준다`() {
        val storage = LocalFileBackupStorage(dir)

        val size = storage.write("backup-aaaaaaaa.sql.gz") { it.write(byteArrayOf(1, 2, 3)) }

        assertEquals(3, size)
        assertContentEquals(byteArrayOf(1, 2, 3), storage.read("backup-aaaaaaaa.sql.gz").use { it.readBytes() })
    }

    @Test
    fun `쓰다 실패하면 파일이 남지 않는다`() {
        val storage = LocalFileBackupStorage(dir)

        assertFailsWith<IllegalStateException> {
            storage.write("backup-bbbbbbbb.sql.gz") { error("덤프 실패") }
        }

        assertFalse(Files.exists(dir.resolve("backup-bbbbbbbb.sql.gz")))
        assertEquals(0, Files.list(dir).use { it.count() })
    }

    @Test
    fun `지운 뒤 다시 지워도 오류가 아니다`() {
        val storage = LocalFileBackupStorage(dir)
        storage.write("backup-cccccccc.sql.gz") { it.write(1) }

        storage.delete("backup-cccccccc.sql.gz")
        storage.delete("backup-cccccccc.sql.gz")

        assertFalse(Files.exists(dir.resolve("backup-cccccccc.sql.gz")))
    }

    @Test
    fun `저장소 밖을 가리키는 키는 거절한다`() {
        val storage = LocalFileBackupStorage(dir)

        assertFailsWith<IllegalArgumentException> { storage.read("../secret") }
        assertFailsWith<IllegalArgumentException> { storage.delete("a/b") }
    }
}
