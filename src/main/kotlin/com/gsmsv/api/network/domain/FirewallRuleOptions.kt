package com.gsmsv.api.network.domain

/** 규칙에 맞은 패킷을 어떻게 할지. [wire]는 API와 Go 내부 API에 쓰는 소문자 값이다 */
enum class FirewallAction(
    val wire: String,
) {
    ACCEPT("accept"),
    DROP("drop"),
    REJECT("reject"),
    ;

    companion object {
        fun fromWire(value: String): FirewallAction? = entries.find { it.wire == value }
    }
}

/** 들어오는 패킷(`in`)인지 나가는 패킷(`out`)인지 */
enum class FirewallDirection(
    val wire: String,
) {
    IN("in"),
    OUT("out"),
    ;

    companion object {
        fun fromWire(value: String): FirewallDirection? = entries.find { it.wire == value }
    }
}

enum class FirewallProtocol(
    val wire: String,
) {
    TCP("tcp"),
    UDP("udp"),
    ICMP("icmp"),
    ;

    companion object {
        fun fromWire(value: String): FirewallProtocol? = entries.find { it.wire == value }
    }
}
