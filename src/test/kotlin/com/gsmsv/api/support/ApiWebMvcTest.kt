package com.gsmsv.api.support

import com.gsmsv.api.global.config.RequestIdFilter
import com.gsmsv.api.global.security.AccessTokenProvider
import com.gsmsv.api.global.security.JwtProperties
import com.gsmsv.api.global.security.SecurityConfig
import com.gsmsv.api.global.security.SecurityErrorResponder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.AliasFor
import kotlin.reflect.KClass

/**
 * 컨트롤러 테스트용 `@WebMvcTest`. 실제 인증 필터·오류 형식·요청 id를 그대로 켜고, [TestTokens]로 토큰을 만들 수 있게 한다.
 *
 * [controllers]를 꼭 적는다. 비워 두면 테스트 소스의 다른 테스트용 컨트롤러까지 함께 떠서 엔드포인트 검사에 걸린다.
 * 로더·reader 같은 도메인 빈은 테스트 클래스에 `@Import`로 따로 넣는다.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@WebMvcTest(properties = ["gsmsv.security.jwt.secret=${TestTokens.SECRET}"])
@EnableConfigurationProperties(JwtProperties::class)
@Import(
    SecurityConfig::class,
    AccessTokenProvider::class,
    SecurityErrorResponder::class,
    RequestIdFilter::class,
    TestTokens::class,
)
annotation class ApiWebMvcTest(
    @get:AliasFor(annotation = WebMvcTest::class, attribute = "controllers")
    vararg val controllers: KClass<*>,
)
