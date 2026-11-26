package com.gsmsv.api.user.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ProfileImageProperties::class)
class ProfileImageConfig
