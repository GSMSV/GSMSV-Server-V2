package com.gsmsv.api.global.grn

import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

/**
 * 경로 변수·요청 파라미터 문자열을 [Grn]으로 바꾼다. Spring Boot가 Converter 빈을 MVC에 자동으로 등록한다.
 *
 * Spring MVC는 경로 변수를 디코딩한 뒤 넘기므로 `grn:...`과 `grn%3A...`이 같은 값으로 들어온다.
 * 형식 오류는 [GrnParser]의 BusinessException이 감싸여 나가고, 예외 핸들러가 꺼내 400으로 돌려준다.
 */
@Component
class StringToGrnConverter : Converter<String, Grn> {
    override fun convert(source: String): Grn = Grn.parse(source)
}
