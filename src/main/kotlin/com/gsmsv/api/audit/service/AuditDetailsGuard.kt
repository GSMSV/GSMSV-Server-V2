package com.gsmsv.api.audit.service

/**
 * 감사 로그 부가 정보에 비밀값과 IP 주소가 들어가지 않게 막는 마지막 안전장치.
 * 호출하는 쪽이 넣지 않는 것이 먼저고, 이것은 실수를 개발 중에 드러내는 용도다.
 */
internal object AuditDetailsGuard {
    // 키 이름에 이 낱말이 있으면 비밀값으로 본다
    private val SECRET_KEY = Regex("password|passwd|secret|token|credential|api_?key|private_?key", RegexOption.IGNORE_CASE)

    // 점으로 나뉜 숫자 4개(IPv4), `::`이 있는 IPv6, 8묶음 IPv6. 시각(`12:30:45`)은 걸리지 않게 좁혔다
    private val IP_VALUE =
        Regex("""\b\d{1,3}(\.\d{1,3}){3}\b|[0-9a-fA-F:]*::[0-9a-fA-F:]*|\b([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\b""")

    fun verify(details: Map<String, String>) {
        details.forEach { (key, value) ->
            require(!SECRET_KEY.containsMatchIn(key)) { "감사 로그 부가 정보에 비밀값을 넣을 수 없습니다: $key" }
            require(!IP_VALUE.containsMatchIn(value)) { "감사 로그 부가 정보에 IP 주소를 넣을 수 없습니다: $key" }
        }
    }
}
