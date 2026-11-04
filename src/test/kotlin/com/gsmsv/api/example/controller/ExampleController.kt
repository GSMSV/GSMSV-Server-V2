package com.gsmsv.api.example.controller

import com.gsmsv.api.example.domain.Example
import com.gsmsv.api.example.dto.ExampleResponse
import com.gsmsv.api.example.service.ExampleService
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Authenticated
import com.gsmsv.api.global.authorization.RequirePermission
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.ServiceResourceReader
import com.gsmsv.api.global.grn.Grn
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import jakarta.validation.Valid
import org.springframework.boot.test.context.TestComponent
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 도메인 컨트롤러의 본보기. 계약에 명세가 없는 테스트 전용 API다 (README "도메인 컨트롤러 만들기").
 *
 * - 메서드마다 `@RequirePermission`·`@Authenticated`·`@Public` 중 하나만 붙인다
 * - `{grn}`은 [Grn]으로 받는다. 형식 검사는 변환기가 끝낸다
 * - 로더에는 어노테이션과 같은 [Action]을 넘기고, 로더가 돌려준 자원 객체를 service에 넘긴다
 *
 * 테스트 소스의 최상위 컨트롤러라 [TestComponent]로 컴포넌트 스캔에서 뺀다. 빼지 않으면 전체 컨텍스트 테스트가
 * 이 컨트롤러를 잡고, 테스트 설정에만 있는 로더를 찾지 못해 뜨지 않는다. 테스트에서는 `ExampleTestConfig`가 넣는다.
 * 실제 도메인 컨트롤러(main 소스)에는 붙이지 않는다.
 */
@TestComponent
@RestController
@RequestMapping("/api/v2/examples")
class ExampleController(
    private val loader: ServiceResourceLoader,
    private val reader: ServiceResourceReader<Example>,
    private val exampleService: ExampleService,
) {
    @Authenticated
    @GetMapping
    fun list(
        @CurrentUser user: AuthenticatedUser,
        @Valid @ModelAttribute page: CursorPageRequest,
    ): CursorPage<ExampleResponse> = exampleService.list(user.toActor(), page)

    @RequirePermission(Action.VIEW)
    @GetMapping("/{grn}")
    fun get(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
    ): ExampleResponse {
        val actor = user.toActor()
        val example = loader.load(actor, grn.value, Action.VIEW, reader)
        return exampleService.get(actor, example)
    }

    @RequirePermission(Action.DELETE)
    @DeleteMapping("/{grn}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
    ) {
        val example = loader.load(user.toActor(), grn.value, Action.DELETE, reader)
        exampleService.delete(example)
    }
}
