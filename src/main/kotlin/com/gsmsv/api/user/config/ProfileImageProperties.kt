package com.gsmsv.api.user.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path

@ConfigurationProperties("gsmsv.user.profile-image")
data class ProfileImageProperties(
    val storageDir: Path = Path.of("./data/profile-images"),
    val maxFileSize: Long = 2 * 1024 * 1024,
    val maxPixels: Int = 256,
    val maxSourcePixels: Long = 16_777_216,
)
