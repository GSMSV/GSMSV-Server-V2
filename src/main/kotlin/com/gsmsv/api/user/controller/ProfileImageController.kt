package com.gsmsv.api.user.controller

import com.gsmsv.api.global.authorization.Authenticated
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.user.service.UserProfileService
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/files/profile-images")
class ProfileImageController(
    private val service: UserProfileService,
) {
    @Authenticated
    @GetMapping("/{file_name}", produces = [MediaType.IMAGE_PNG_VALUE])
    fun get(
        @CurrentUser user: AuthenticatedUser,
        @PathVariable("file_name") fileName: String,
    ): ResponseEntity<ByteArray> =
        ResponseEntity
            .ok()
            .cacheControl(CacheControl.noCache())
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.IMAGE_PNG)
            .body(service.readProfileImage(user, fileName))
}
