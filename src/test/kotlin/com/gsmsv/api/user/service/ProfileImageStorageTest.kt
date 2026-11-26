package com.gsmsv.api.user.service

import com.gsmsv.api.user.config.ProfileImageProperties
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProfileImageStorageTest {
    @Test
    fun `허용된 PNG는 정규화해서 저장한다`() {
        val directory = Files.createTempDirectory("profile-image-test")
        val storage = LocalProfileImageStorage(ProfileImageProperties(storageDir = directory))

        val fileName = storage.save(imageBytes(2, 2))

        assertTrue(Files.exists(directory.resolve(fileName)))
        assertTrue(ImageIO.read(directory.resolve(fileName).toFile()).width == 256)
    }

    @Test
    fun `허용되지 않은 형식은 저장하지 않는다`() {
        val directory = Files.createTempDirectory("profile-image-test")
        val storage = LocalProfileImageStorage(ProfileImageProperties(storageDir = directory))

        assertFailsWith<IllegalArgumentException> { storage.save("not-an-image".toByteArray()) }
    }

    @Test
    fun `원본 픽셀 수가 제한을 넘으면 디코딩하지 않는다`() {
        val directory = Files.createTempDirectory("profile-image-test")
        val storage =
            LocalProfileImageStorage(
                ProfileImageProperties(storageDir = directory, maxSourcePixels = 1),
            )

        assertFailsWith<IllegalArgumentException> { storage.save(imageBytes(2, 2)) }
    }

    @Test
    fun `WebP를 실제로 디코딩해서 저장한다`() {
        val directory = Files.createTempDirectory("profile-image-test")
        val storage = LocalProfileImageStorage(ProfileImageProperties(storageDir = directory))

        val source = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
        val webpBytes =
            java.io.ByteArrayOutputStream().use { output ->
                check(ImageIO.write(source, "webp", output))
                output.toByteArray()
            }
        val fileName = storage.save(webpBytes)

        assertTrue(ImageIO.read(directory.resolve(fileName).toFile()).width == 256)
    }

    private fun imageBytes(
        width: Int,
        height: Int,
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        return java.io.ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }
}
