package com.gsmsv.api.example.service

import com.gsmsv.api.example.domain.Example
import com.gsmsv.api.example.dto.ExampleResponse
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Actor
import com.gsmsv.api.global.authorization.PermissionsResolver
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.ServiceResourceReader
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.response.Cursor
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.global.response.CursorPageRequest

/**
 * service는 id가 아니라 로더가 돌려준 자원 객체를 받는다. 권한 판단은 이미 끝났으므로 여기서 소유자·역할을 다시 비교하지 않는다.
 *
 * 실제 도메인에서는 `@Service`를 붙이고, 조회는 `@Transactional(readOnly = true)`, 변경은 `@Transactional`을 붙인다.
 */
class ExampleService(
    private val loader: ServiceResourceLoader,
    private val reader: ServiceResourceReader<Example>,
    private val permissionsResolver: PermissionsResolver,
) {
    fun get(
        actor: Actor,
        example: Example,
    ): ExampleResponse = toResponse(actor, example)

    /** 목록은 `listVisible`로만 꺼낸다. 볼 수 있는 자원만 돌려준다 */
    fun list(
        actor: Actor,
        page: CursorPageRequest,
    ): CursorPage<ExampleResponse> {
        val cursor = page.decodedCursor()
        // 실제 repository는 이 정렬·커서 조건과 `limit + 1`을 쿼리로 처리한다
        val rows =
            loader
                .listVisible(actor, reader)
                .sortedWith(compareByDescending<Example> { it.createdAt }.thenByDescending { it.id })
                .filter { cursor == null || isAfter(it, cursor) }
                .take(page.limit + 1)
        return CursorPage.of(rows, page.limit, { Cursor(it.createdAt, it.id) }) { toResponse(actor, it) }
    }

    fun delete(example: Example) {
        // 권한과 상관없는 도메인 규칙 위반은 BusinessException으로 던진다. 공통 오류 형식은 예외 핸들러가 만든다
        if (example.locked) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, message = "잠긴 자원은 삭제할 수 없습니다.")
        }
        // 실제 삭제는 repository와 Infra-V2 클라이언트를 부른다
    }

    private fun toResponse(
        actor: Actor,
        example: Example,
    ): ExampleResponse = ExampleResponse.of(example, permissionsResolver.resolve(actor, example, RESPONSE_ACTIONS))

    private fun isAfter(
        example: Example,
        cursor: Cursor,
    ): Boolean = example.createdAt < cursor.createdAt || (example.createdAt == cursor.createdAt && example.id < cursor.id)

    private companion object {
        /** 응답 `permissions`에 담을 동작. 프론트가 버튼을 그릴 동작만 고른다 */
        val RESPONSE_ACTIONS = listOf(Action.DELETE, Action.EXTEND)
    }
}
