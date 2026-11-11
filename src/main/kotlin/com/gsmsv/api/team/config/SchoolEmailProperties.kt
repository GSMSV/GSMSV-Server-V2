package com.gsmsv.api.team.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("gsmsv.team")
data class SchoolEmailProperties(
    @field:NotBlank
    val emailDomain: String,
)
