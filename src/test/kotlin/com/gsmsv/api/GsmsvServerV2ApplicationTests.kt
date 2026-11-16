package com.gsmsv.api

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
// 테스트 전용 서명 키다. 실제 서버의 키가 아니다
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="])
@Tag("integration")
class GsmsvServerV2ApplicationTests {
    @Test
    fun contextLoads() {
    }
}
