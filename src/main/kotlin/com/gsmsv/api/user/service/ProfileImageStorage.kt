package com.gsmsv.api.user.service

import com.gsmsv.api.user.config.ProfileImageProperties
import org.springframework.stereotype.Component
import java.awt.Image
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO

interface ProfileImageStorage {
    fun save(bytes: ByteArray): String

    fun read(fileName: String): ByteArray?

    fun delete(fileName: String)
}

@Component
class LocalProfileImageStorage(
    private val properties: ProfileImageProperties,
) : ProfileImageStorage {
    override fun save(bytes: ByteArray): String {
        Files.createDirectories(properties.storageDir)
        val image = ImageIO.read(bytes.inputStream()) ?: return throw IllegalArgumentException("unsupported image")
        val side = properties.maxPixels
        val output = BufferedImage(side, side, BufferedImage.TYPE_INT_RGB)
        val graphics = output.createGraphics()
        try {
            val scale = maxOf(side.toDouble() / image.width, side.toDouble() / image.height)
            val width = (image.width * scale).toInt()
            val height = (image.height * scale).toInt()
            val x = (side - width) / 2
            val y = (side - height) / 2
            graphics.drawImage(image, x, y, width, height, null)
        } finally {
            graphics.dispose()
        }
        val fileName = "${UUID.randomUUID()}.png"
        val target = safePath(fileName)
        ImageIO.write(output, "png", target.toFile())
        return fileName
    }

    override fun read(fileName: String): ByteArray? {
        val path = safePath(fileName)
        return if (Files.isRegularFile(path)) Files.readAllBytes(path) else null
    }

    override fun delete(fileName: String) {
        Files.deleteIfExists(safePath(fileName))
    }

    private fun safePath(fileName: String): Path {
        require(fileName.matches(FILE_NAME))
        return properties.storageDir.resolve(fileName).normalize()
    }

    private companion object {
        val FILE_NAME = Regex("[0-9a-f-]{36}\\.png")
    }
}
