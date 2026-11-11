package com.gsmsv.api.database.controller

import com.gsmsv.api.database.dto.DatabaseBackupListResponse
import com.gsmsv.api.database.service.DatabaseBackupApiService
import com.gsmsv.api.database.service.DatabaseResourceReader
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.RequirePermission
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.grn.Grn
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/databases/{grn}/backups")
class DatabaseBackupController(
    private val loader: ServiceResourceLoader,
    private val reader: DatabaseResourceReader,
    private val backupApiService: DatabaseBackupApiService,
) {
    /** 7.10 백업 목록. 개인 본인·팀 팀원 전원이 본다 */
    @RequirePermission(Action.VIEW)
    @GetMapping
    fun list(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
    ): DatabaseBackupListResponse {
        val database = loader.load(user.toActor(), grn.value, Action.VIEW, reader)
        return backupApiService.list(database)
    }
}
