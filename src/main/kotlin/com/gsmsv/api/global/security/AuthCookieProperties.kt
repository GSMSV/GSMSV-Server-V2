package com.gsmsv.api.global.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("gsmsv.security.cookie")
data class AuthCookieProperties(
    val secure: Boolean = true,
)
