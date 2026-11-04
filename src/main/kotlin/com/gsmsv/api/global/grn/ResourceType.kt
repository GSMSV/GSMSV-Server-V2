package com.gsmsv.api.global.grn

/**
 * GRN을 갖는 서비스 자원 종류. [prefix]는 자원 id의 접두사다.
 *
 * 접두사는 한 번 정하면 바꾸지 않는다 (Contracts-V2 `constants/resource-id.md` 4장).
 */
enum class ResourceType(
    val prefix: String,
) {
    VM("vm"),
    FUNCTION("fn"),
    DATABASE("db"),
    APP("app"),
    ;

    companion object {
        fun fromPrefix(prefix: String): ResourceType? = entries.find { it.prefix == prefix }
    }
}
