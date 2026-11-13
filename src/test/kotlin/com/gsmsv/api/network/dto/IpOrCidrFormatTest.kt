package com.gsmsv.api.network.dto

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IpOrCidrFormatTest {
    @ParameterizedTest
    @ValueSource(strings = ["203.0.113.5", "203.0.113.0/24", "0.0.0.0/0", "198.51.100.7/32", "2001:db8::1", "2001:db8::/32", "::/0"])
    fun `IP 주소와 CIDR은 맞는 형식이다`(value: String) {
        assertTrue(IpOrCidrFormat.isValid(value))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "example.com",
            "localhost",
            "256.0.0.1",
            "203.0.113",
            "203.0.113.0/33",
            "203.0.113.0/",
            "203.0.113.0/024",
            "203.0.113.0/24/1",
            "01.2.3.4",
            "2001:db8::/129",
            "2001:zz8::1",
            "fe80::1%eth0",
            " 203.0.113.5",
        ],
    )
    fun `IP·CIDR이 아니면 틀린 형식이다`(value: String) {
        assertFalse(IpOrCidrFormat.isValid(value))
    }
}
