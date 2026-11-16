package com.gsmsv.api.database.backup

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** 로컬 디스크에 저장한다. [key]는 우리가 만든 id 기반 이름만 받고, 디렉터리 밖을 가리키면 거절한다 */
class LocalFileBackupStorage(
    directory: Path,
) : BackupStorage {
    private val root: Path = directory.toAbsolutePath().normalize().also { Files.createDirectories(it) }

    override fun write(
        key: String,
        writer: (OutputStream) -> Unit,
    ): Long {
        val target = resolve(key)
        val temp = Files.createTempFile(root, "backup-", ".part")
        try {
            Files.newOutputStream(temp).use(writer)
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return Files.size(target)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    override fun read(key: String): InputStream = Files.newInputStream(resolve(key))

    override fun delete(key: String) {
        Files.deleteIfExists(resolve(key))
    }

    private fun resolve(key: String): Path {
        val path = root.resolve(key).normalize()
        require(path.parent == root) { "저장소 밖을 가리키는 키입니다." }
        return path
    }
}
