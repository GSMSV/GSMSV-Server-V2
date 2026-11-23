package com.gsmsv.api.app.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("gsmsv.app.subdomain")
data class AppSubdomainProperties(
    val reserved: Set<String> = emptySet(),
)
