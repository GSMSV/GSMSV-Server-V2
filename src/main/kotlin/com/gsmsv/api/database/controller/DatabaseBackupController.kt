package com.gsmsv.api.database.controller

import com.gsmsv.api.database.dto.DatabaseBackupListResponse
import com.gsmsv.api.database.service.DatabaseBackupApiService
import com.gsmsv.api.database.service.DatabaseBackupResourceReader
import com.gsmsv.api.database.service.DatabaseResourceReader
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.RequirePermission
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.authorization.SubResourceLoader
import com.gsmsv.api.global.grn.Grn
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import org.springframework.core.io.InputStreamResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/databases/{grn}/backups")
class DatabaseBackupController(
    private val loader: ServiceResourceLoader,
    private val subResourceLoader: SubResourceLoader,
    private val reader: DatabaseResourceReader,
    private val backupReader: DatabaseBackupResourceReader,
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

    /**
     * 7.11 백업 내려받기. 개인 본인·팀 팀원 전원이 받는다. 다른 DB의 백업 id면 로더가 404를 낸다.
     *
     * 큰 파일을 메모리에 올리지 않도록 스트림을 그대로 흘려보낸다.
     */
    @RequirePermission(Action.READ_BACKUP)
    @GetMapping("/{backupId}/download")
    fun download(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable grn: Grn,
        @PathVariable backupId: String,
    ): ResponseEntity<InputStreamResource> {
        val actor = user.toActor()
        val loaded = subResourceLoader.load(actor, grn.value, backupId, Action.READ_BACKUP, reader, backupReader)
        val file = backupApiService.download(actor, loaded)
        return ResponseEntity
            .ok()
            .contentType(GZIP)
            .contentLength(file.sizeBytes)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(file.fileName)
                    .build()
                    .toString(),
            ).body(InputStreamResource(file.content))
    }

    private companion object {
        val GZIP: MediaType = MediaType.parseMediaType("application/gzip")
    }
}
