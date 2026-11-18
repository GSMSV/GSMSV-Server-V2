package com.gsmsv.api.operation.controller

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.OperationLoader
import com.gsmsv.api.global.authorization.RequirePermission
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.operation.dto.OperationResponse
import com.gsmsv.api.operation.service.OperationResourceReader
import com.gsmsv.api.operation.service.OperationStreamService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/** 오래 걸리는 작업의 상태 (Contracts-V2 `openapi/operation.yaml`). 권한은 대상 자원의 조회 권한이다 */
@RestController
@RequestMapping("/api/v2/operations")
class OperationController(
    private val loader: OperationLoader,
    private val reader: OperationResourceReader,
    private val streamService: OperationStreamService,
) {
    @RequirePermission(Action.VIEW)
    @GetMapping("/{operationId}")
    fun get(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable operationId: String,
    ): OperationResponse = OperationResponse.from(loader.load(user.toActor(), operationId, Action.VIEW, reader))

    /** 권한 판단은 스트림을 열기 전에 한다. 실패하면 SSE가 아니라 JSON 오류로 답한다 */
    @RequirePermission(Action.VIEW)
    @GetMapping("/{operationId}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable operationId: String,
    ): SseEmitter = streamService.subscribe(loader.load(user.toActor(), operationId, Action.VIEW, reader))
}
