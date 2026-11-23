package com.gsmsv.api.app.controller

import com.gsmsv.api.app.dto.SubdomainAvailabilityResponse
import com.gsmsv.api.app.service.SubdomainAvailabilityService
import com.gsmsv.api.global.authorization.Authenticated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class SubdomainAvailabilityController(
    private val service: SubdomainAvailabilityService,
) {
    @Authenticated
    @GetMapping("/api/v2/apps/subdomains/availability")
    fun check(
        @RequestParam name: String,
    ): SubdomainAvailabilityResponse = service.check(name)
}
