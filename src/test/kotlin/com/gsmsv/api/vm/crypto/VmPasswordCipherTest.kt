package com.gsmsv.api.vm.crypto

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class VmPasswordCipherTest {
    // 테스트 전용 키다 (32바이트를 Base64로 인코딩한 값)
    private val cipher = VmPasswordCipher("dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q=")

    @Test
    fun `암호화한 비밀번호는 평문과 다르고 복호화하면 돌아온다`() {
        val encrypted = cipher.encrypt("초기-비밀번호")

        assertNotEquals("초기-비밀번호", encrypted)
        assertEquals("초기-비밀번호", cipher.decrypt(encrypted))
    }
}
