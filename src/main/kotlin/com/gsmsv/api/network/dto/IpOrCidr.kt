package com.gsmsv.api.network.dto

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.reflect.KClass

/** IPv4·IPv6 주소, 또는 그 CIDR (`203.0.113.0/24`). null은 통과한다 (선택 칸) */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [IpOrCidrValidator::class])
annotation class IpOrCidr(
    val message: String = "IP 주소 또는 CIDR이어야 합니다.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class IpOrCidrValidator : ConstraintValidator<IpOrCidr, String?> {
    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext,
    ): Boolean = value == null || IpOrCidrFormat.isValid(value)
}

object IpOrCidrFormat {
    // IPv4를 끼운 IPv6 전체 표기(0000:0000:0000:0000:0000:ffff:255.255.255.255/128)가 49자다
    private const val MAX_LENGTH = 49
    private val IPV4 = Regex("^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$")

    // 이 글자만 허용해야 InetAddress가 이름으로 보고 DNS를 찾지 않는다 (콜론이 있으면 IPv6 표기로만 해석한다)
    // 첫 글자가 16진수나 콜론이어야 JDK가 리터럴로 읽는다 (`.`으로 시작하면 호스트 이름으로 보고 DNS를 찾는다)
    private val IPV6_CHARS = Regex("^(?=[0-9a-fA-F:])[0-9a-fA-F:.]*:[0-9a-fA-F:.]*$")
    private val PREFIX = Regex("^(0|[1-9][0-9]{0,2})$")

    fun isValid(value: String): Boolean {
        if (value.isEmpty() || value.length > MAX_LENGTH) return false
        val slash = value.indexOf('/')
        val address = if (slash < 0) value else value.substring(0, slash)
        val prefix = if (slash < 0) null else value.substring(slash + 1)
        val maxPrefix =
            when {
                IPV4.matches(address) -> 32
                isIpv6(address) -> 128
                else -> return false
            }
        if (prefix == null) return true
        return PREFIX.matches(prefix) && prefix.toInt() <= maxPrefix
    }

    private fun isIpv6(address: String): Boolean {
        if (!IPV6_CHARS.matches(address)) return false
        // 콜론이 있으면 IPv6 표기다. ::ffff:1.2.3.4는 JDK가 Inet4Address로 돌려주므로 결과 타입은 보지 않는다
        return try {
            InetAddress.getByName(address)
            true
        } catch (_: UnknownHostException) {
            false
        }
    }
}
