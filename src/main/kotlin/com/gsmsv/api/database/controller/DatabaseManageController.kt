package com.gsmsv.api.database.controller

import com.gsmsv.api.database.dto.DatabaseExtendResponse
import com.gsmsv.api.database.dto.RenameDatabaseRequest
import com.gsmsv.api.database.dto.RenameDatabaseResponse
import com.gsmsv.api.database.service.DatabaseManageService
import com.gsmsv.api.database.service.DatabaseResourceReader
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.RequirePermission
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.grn.Grn
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/databases/{grn}")
class DatabaseManageController(
    private val loader: ServiceResourceLoader,
    private val reader: DatabaseResourceReader,
    private val manageService: DatabaseManageService,
) {
    /** 7.7 이름 수정. 개인 본인·팀 팀장·DevOps만 한다 */
    @RequirePermission(Action.UPDATE)
    @PatchMapping
    fun rename(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
        @Valid @RequestBody request: RenameDatabaseRequest,
    ): RenameDatabaseResponse {
        val database = loader.load(user.toActor(), grn.value, Action.UPDATE, reader)
        return manageService.rename(database, request.name)
    }

    /** 7.9 사용 기간 연장. 개인 본인·팀 팀원 전원이 한다 */
    @RequirePermission(Action.EXTEND)
    @PostMapping("/extend")
    fun extend(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
    ): DatabaseExtendResponse {
        val database = loader.load(user.toActor(), grn.value, Action.EXTEND, reader)
        return manageService.extend(database)
    }
}
