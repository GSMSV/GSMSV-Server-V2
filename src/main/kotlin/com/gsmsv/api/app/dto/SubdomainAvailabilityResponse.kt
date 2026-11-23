package com.gsmsv.api.app.dto

data class SubdomainAvailabilityResponse(
    val available: Boolean,
    val reason: String?,
)
