package com.gsmsv.api.vm.crypto

import com.gsmsv.api.database.crypto.DatabasePasswordCipher

/**
 * VM 초기 비밀번호를 저장할 때 쓰는 복호화 가능한 암호화 (인스턴스 4.7).
 *
 * 방식은 [DatabasePasswordCipher]와 같은 AES-256-GCM이다. DB 키와 섞이지 않게 타입과 키를 따로 둔다.
 * [base64Key]는 `openssl rand -base64 32`로 만든 값이고 환경 변수로만 받는다.
 */
class VmPasswordCipher(
    base64Key: String,
) {
    private val delegate = DatabasePasswordCipher(base64Key)

    fun encrypt(plain: String): String = delegate.encrypt(plain)

    fun decrypt(stored: String): String = delegate.decrypt(stored)
}
