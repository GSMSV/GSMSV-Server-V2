package com.gsmsv.api.database.crypto

import org.junit.jupiter.api.Test
import java.security.GeneralSecurityException
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class DatabasePasswordCipherTest {
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val otherKey = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })

    @Test
    fun `암호화한 비밀번호를 같은 키로 복호화하면 원래 값이 나온다`() {
        val cipher = DatabasePasswordCipher(key)

        val stored = cipher.encrypt("Abc123Secret")

        assertNotEquals("Abc123Secret", stored)
        assertEquals("Abc123Secret", cipher.decrypt(stored))
    }

    @Test
    fun `같은 값을 두 번 암호화하면 결과가 다르다`() {
        val cipher = DatabasePasswordCipher(key)

        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"))
    }

    @Test
    fun `다른 키로는 복호화하지 못한다`() {
        val stored = DatabasePasswordCipher(key).encrypt("secret")

        assertFailsWith<GeneralSecurityException> { DatabasePasswordCipher(otherKey).decrypt(stored) }
    }

    @Test
    fun `32바이트가 아닌 키는 거절한다`() {
        assertFailsWith<IllegalArgumentException> {
            DatabasePasswordCipher(Base64.getEncoder().encodeToString(ByteArray(16)))
        }
    }

    @Test
    fun `생성한 비밀번호는 SQL에 넣어도 안전한 글자만 쓴다`() {
        repeat(20) {
            assertEquals(true, Regex("^[A-Za-z0-9]{32}$").matches(DatabasePasswordGenerator.generate()))
        }
    }
}
