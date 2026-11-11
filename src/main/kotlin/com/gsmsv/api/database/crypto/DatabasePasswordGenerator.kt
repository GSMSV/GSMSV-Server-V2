package com.gsmsv.api.database.crypto

import java.security.SecureRandom

/** DB 계정 비밀번호를 무작위로 만든다. SQL에 그대로 넣어도 안전하도록 영문 대소문자와 숫자만 쓴다 */
object DatabasePasswordGenerator {
    private const val LENGTH = 32
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val random = SecureRandom()

    fun generate(): String = String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })
}
