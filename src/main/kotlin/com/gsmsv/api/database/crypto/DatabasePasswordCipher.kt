package com.gsmsv.api.database.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * DB 접속 비밀번호를 저장할 때 쓰는 복호화 가능한 암호화 (24번 DBaaS 7.5).
 *
 * 접속 정보를 다시 보여 줘야 해서 해시가 아니라 AES-256-GCM으로 암호화한다.
 * 결과는 `Base64(nonce 12바이트 + 암호문 + 인증 태그)` 한 문자열이다. 값마다 nonce를 새로 뽑는다.
 *
 * [base64Key]는 Base64로 인코딩한 32바이트 키다 (`openssl rand -base64 32`). 환경 변수로만 받는다.
 */
class DatabasePasswordCipher(
    base64Key: String,
) {
    private val key: SecretKeySpec

    init {
        val bytes =
            try {
                Base64.getDecoder().decode(base64Key)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("DB 비밀번호 암호화 키는 Base64여야 합니다.", e)
            }
        require(bytes.size == KEY_BYTES) { "DB 비밀번호 암호화 키는 32바이트여야 합니다." }
        key = SecretKeySpec(bytes, "AES")
    }

    fun encrypt(plain: String): String {
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(nonce + encrypted)
    }

    /** 키가 다르거나 값이 바뀌었으면 [java.security.GeneralSecurityException]을 던진다 */
    fun decrypt(stored: String): String {
        val bytes = Base64.getDecoder().decode(stored)
        require(bytes.size > NONCE_BYTES) { "암호문이 너무 짧습니다." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, NONCE_BYTES)))
        return String(cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES), Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        val random = SecureRandom()
    }
}
