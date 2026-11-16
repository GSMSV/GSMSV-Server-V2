package com.gsmsv.api.vm.config

import com.gsmsv.api.vm.crypto.VmPasswordCipher
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class VmCryptoConfig {
    /** 키는 환경 변수로만 받는다. 설정하지 않으면 빈이 없고, 비밀번호를 저장하는 기능이 기동에 실패한다 */
    @Bean
    @ConditionalOnProperty("gsmsv.vm.encryption-key")
    fun vmPasswordCipher(
        @Value("\${gsmsv.vm.encryption-key}") key: String,
    ): VmPasswordCipher = VmPasswordCipher(key)
}
