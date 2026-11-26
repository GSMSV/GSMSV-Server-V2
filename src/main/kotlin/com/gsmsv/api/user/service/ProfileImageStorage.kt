package com.gsmsv.api.user.service

import com.gsmsv.api.user.config.ProfileImageProperties
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
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
        validateFormat(bytes)
        Files.createDirectories(properties.storageDir)
        val image = readImage(bytes)
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

    private fun validateFormat(bytes: ByteArray) {
        val jpeg = bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        val png = bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)
        val webp =
            bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())
        if (!jpeg && !png && !webp) throw IllegalArgumentException("unsupported image")
    }

    private fun readImage(bytes: ByteArray): BufferedImage {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val reader =
                input?.let { ImageIO.getImageReaders(it).asSequence().firstOrNull() }
                    ?: throw IllegalArgumentException("unsupported image")
            try {
                reader.setInput(input, true, true)
                val width = reader.getWidth(0).toLong()
                val height = reader.getHeight(0).toLong()
                if (width * height > properties.maxSourcePixels) throw IllegalArgumentException("image too large")
                return try {
                    ImageIO.read(ByteArrayInputStream(bytes))
                        ?: throw IllegalArgumentException("unsupported image")
                } catch (_: IOException) {
                    throw IllegalArgumentException("unsupported image")
                }
            } finally {
                reader.dispose()
            }
        }
    }

    private companion object {
        val FILE_NAME = Regex("[0-9a-f-]{36}\\.png")
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
