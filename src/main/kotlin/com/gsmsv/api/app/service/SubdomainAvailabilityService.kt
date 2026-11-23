package com.gsmsv.api.app.service

import com.gsmsv.api.app.config.AppSubdomainProperties
import com.gsmsv.api.app.dto.SubdomainAvailabilityResponse
import com.gsmsv.api.app.repository.SubdomainAvailabilityRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SubdomainAvailabilityService(
    private val properties: AppSubdomainProperties,
    private val repository: SubdomainAvailabilityRepository,
) {
    @Transactional(readOnly = true)
    fun check(name: String): SubdomainAvailabilityResponse =
        when {
            !NAME_PATTERN.matches(name) -> SubdomainAvailabilityResponse(false, "invalid_format")
            name in RESERVED || name in properties.reserved -> SubdomainAvailabilityResponse(false, "reserved")
            repository.existsByName(name) -> SubdomainAvailabilityResponse(false, "taken")
            else -> SubdomainAvailabilityResponse(true, null)
        }

    private companion object {
        val NAME_PATTERN = Regex("^[a-z](?!.*--)[a-z0-9-]{1,61}[a-z0-9]$")
        val RESERVED = setOf("www", "api", "admin", "gsmsv", "login", "auth", "official", "support", "help", "status", "docs", "mail")
    }
}
