package com.gsmsv.api.user.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize
import java.nio.file.Path

@ConfigurationProperties("gsmsv.user.profile-image")
data class ProfileImageProperties(
    val storageDir: Path = Path.of("./data/profile-images"),
    val maxFileSize: DataSize = DataSize.ofMegabytes(2),
    val maxPixels: Int = 256,
    val maxSourcePixels: Long = 16_777_216,
)
