package com.gsmsv.api.network.domain

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.response.FieldErrorDetail
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * 검사를 마친 방화벽 규칙 입력 (5.15). 빠진 값은 계약의 기본값(`accept`, `in`, `tcp`)으로 채운다.
 *
 * [dport]는 앞의 0을 뗀 `포트` 또는 `시작:끝` 형태로 바꿔 둔다. [source]는 IP 또는 CIDR이고, 없으면 모든 출발지다.
 * 목적지는 받지 않는다. 언제나 그 VM의 내부 IP로 강제한다.
 */
data class FirewallRuleSpec(
    val action: FirewallAction,
    val direction: FirewallDirection,
    val protocol: FirewallProtocol,
    val dport: String?,
    val source: String?,
) {
    companion object {
        private const val MIN_PORT = 1
        private const val MAX_PORT = 65535
        private val DPORT = Regex("^([0-9]{1,5})(?::([0-9]{1,5}))?$")
        private val IPV4 = Regex("^([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})$")
        private val IPV6_CHARS = Regex("^[0-9a-fA-F:.]+$")
        private val PREFIX = Regex("^[0-9]{1,3}$")

        /** @throws BusinessException 형식이 틀린 칸이 있으면 400 [ErrorCode.VALIDATION_FAILED] (칸별 이유를 `details.fields`에 담는다) */
        fun of(
            action: String?,
            direction: String?,
            protocol: String?,
            dport: String?,
            source: String?,
        ): FirewallRuleSpec {
            val errors = mutableListOf<FieldErrorDetail>()
            val parsedAction = parseEnum(action, FirewallAction.ACCEPT, FirewallAction::fromWire, "action", errors)
            val parsedDirection = parseEnum(direction, FirewallDirection.IN, FirewallDirection::fromWire, "direction", errors)
            val parsedProtocol = parseEnum(protocol, FirewallProtocol.TCP, FirewallProtocol::fromWire, "protocol", errors)
            val normalizedDport = dport?.let { normalizeDport(it, errors) }
            if (dport != null && parsedProtocol == FirewallProtocol.ICMP) {
                errors += FieldErrorDetail("dport", "icmp 규칙에는 포트를 지정할 수 없습니다.")
            }
            if (source != null && !isIpOrCidr(source)) {
                errors += FieldErrorDetail("source", "IP 주소 또는 CIDR이어야 합니다.")
            }
            if (errors.isNotEmpty() || parsedAction == null || parsedDirection == null || parsedProtocol == null) {
                throw BusinessException(ErrorCode.VALIDATION_FAILED, details = mapOf("fields" to errors))
            }
            return FirewallRuleSpec(parsedAction, parsedDirection, parsedProtocol, normalizedDport, source)
        }

        private fun <E : Enum<E>> parseEnum(
            value: String?,
            default: E,
            fromWire: (String) -> E?,
            field: String,
            errors: MutableList<FieldErrorDetail>,
        ): E? {
            if (value == null) return default
            return fromWire(value) ?: null.also { errors += FieldErrorDetail(field, "허용하지 않는 값입니다.") }
        }

        private fun normalizeDport(
            value: String,
            errors: MutableList<FieldErrorDetail>,
        ): String? {
            val match = DPORT.matchEntire(value)
            val start = match?.groupValues?.get(1)?.toInt()
            val end =
                match
                    ?.groupValues
                    ?.get(2)
                    ?.takeIf { it.isNotEmpty() }
                    ?.toInt()
            val valid =
                start != null &&
                    start in MIN_PORT..MAX_PORT &&
                    (end == null || (end in MIN_PORT..MAX_PORT && start <= end))
            if (!valid) {
                errors += FieldErrorDetail("dport", "1~65535 사이의 포트 또는 '시작:끝' 범위여야 합니다.")
                return null
            }
            return if (end == null) "$start" else "$start:$end"
        }

        private fun isIpOrCidr(value: String): Boolean {
            val parts = value.split('/')
            if (parts.size > 2) return false
            val address = parts[0]
            val prefix = parts.getOrNull(1)
            if (prefix != null && !PREFIX.matches(prefix)) return false
            val prefixLength = prefix?.toInt()
            return when {
                IPV4.matches(address) -> isIpv4(address) && (prefixLength == null || prefixLength <= 32)
                ':' in address -> isIpv6(address) && (prefixLength == null || prefixLength <= 128)
                else -> false
            }
        }

        private fun isIpv4(address: String): Boolean =
            IPV4
                .matchEntire(address)
                ?.groupValues
                ?.drop(1)
                ?.all { it.toInt() <= 255 && (it.length == 1 || !it.startsWith('0')) } == true

        // ':'가 있는 문자열은 IPv6 리터럴로만 해석되므로 DNS 조회가 일어나지 않는다
        private fun isIpv6(address: String): Boolean {
            if (!IPV6_CHARS.matches(address)) return false
            return try {
                InetAddress.getByName(address) is Inet6Address
            } catch (_: UnknownHostException) {
                false
            }
        }
    }
}
