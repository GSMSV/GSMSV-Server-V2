package com.gsmsv.api.database.controller

import com.gsmsv.api.database.dto.DatabaseCredentialResponse
import com.gsmsv.api.database.dto.PasswordRotationResponse
import com.gsmsv.api.database.service.DatabaseCredentialService
import com.gsmsv.api.database.service.DatabaseResourceReader
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.RequirePermission
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.grn.Grn
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/databases/{grn}")
class DatabaseCredentialController(
    private val loader: ServiceResourceLoader,
    private val reader: DatabaseResourceReader,
    private val credentialService: DatabaseCredentialService,
) {
    /** 7.5 접속 정보·비밀번호. 개인 본인·팀 팀원 전원이 본다 */
    @RequirePermission(Action.VIEW_ACCESS_INFO)
    @GetMapping("/credentials")
    fun credentials(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
    ): ResponseEntity<DatabaseCredentialResponse> {
        val actor = user.toActor()
        val database = loader.load(actor, grn.value, Action.VIEW_ACCESS_INFO, reader)
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(credentialService.credentials(actor, database))
    }

    /** 7.6 비밀번호 교체. 개인 본인·팀 팀장·DevOps만 한다 */
    @RequirePermission(Action.ROTATE_PASSWORD)
    @PostMapping("/password-rotation")
    fun rotatePassword(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
    ): ResponseEntity<PasswordRotationResponse> {
        val actor = user.toActor()
        val database = loader.load(actor, grn.value, Action.ROTATE_PASSWORD, reader)
        // 새 비밀번호가 들어 있으므로 중간 저장소에 남지 않게 한다
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(credentialService.rotate(actor, database))
    }
}
