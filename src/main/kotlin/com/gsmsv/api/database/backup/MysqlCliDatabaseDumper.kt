package com.gsmsv.api.database.backup

import com.gsmsv.api.database.config.SharedDatabaseProperties
import com.gsmsv.api.database.provisioner.DatabaseIdentifier
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

/**
 * `mysqldump`·`mysql` 명령으로 덤프하고 되돌린다. 실행 환경에 두 명령이 있어야 한다.
 *
 * 관리 계정 비밀번호는 명령 인자가 아니라 환경 변수(`MYSQL_PWD`)로 넘겨 프로세스 목록에 드러나지 않게 한다.
 * 오류 출력에 서버 주소가 들어 있을 수 있어 버리고 종료 코드만 예외에 남긴다.
 */
class MysqlCliDatabaseDumper(
    properties: SharedDatabaseProperties,
) : DatabaseDumper {
    private val host: String
    private val port: Int
    private val username = properties.adminUsername
    private val password = properties.adminPassword

    init {
        // jdbc:mysql://host:port/... 에서 주소만 꺼낸다
        val uri = URI(properties.url.removePrefix("jdbc:"))
        host = requireNotNull(uri.host) { "공유 DB 서버 주소를 읽지 못했습니다." }
        port = if (uri.port > 0) uri.port else DEFAULT_PORT
    }

    override fun dump(
        databaseId: String,
        output: OutputStream,
    ) {
        DatabaseIdentifier.require(databaseId)
        val process = start("mysqldump", "--single-transaction", "--routines", "--no-tablespaces", databaseId)
        process.outputStream.close()
        process.inputStream.use { it.copyTo(output) }
        requireSuccess(process, "mysqldump")
    }

    override fun restore(
        databaseId: String,
        input: InputStream,
    ) {
        DatabaseIdentifier.require(databaseId)
        val process = start("mysql", databaseId)
        process.outputStream.use { input.copyTo(it) }
        process.inputStream.readAllBytes()
        requireSuccess(process, "mysql")
    }

    private fun start(
        command: String,
        vararg extra: String,
    ): Process {
        val builder = ProcessBuilder(listOf(command, "--host=$host", "--port=$port", "--user=$username") + extra)
        builder.environment()["MYSQL_PWD"] = password
        builder.redirectError(ProcessBuilder.Redirect.DISCARD)
        return builder.start()
    }

    private fun requireSuccess(
        process: Process,
        command: String,
    ) {
        val exitCode = process.waitFor()
        if (exitCode != 0) throw DatabaseDumpException("$command 명령이 실패했습니다. 종료 코드=$exitCode")
    }

    private companion object {
        const val DEFAULT_PORT = 3306
    }
}
