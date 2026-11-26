package com.gsmsv.api.user.controller

import com.gsmsv.api.global.authorization.Authenticated
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.CurrentUser
import com.gsmsv.api.user.dto.MeResponse
import com.gsmsv.api.user.dto.ProfileImageResponse
import com.gsmsv.api.user.service.UserProfileService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v2/users")
class UserController(
    private val service: UserProfileService,
) {
    @Authenticated
    @GetMapping("/me")
    fun me(
        @CurrentUser user: AuthenticatedUser,
    ): MeResponse = service.getMe(user.userId)

    @Authenticated
    @PutMapping("/me/profile-image")
    fun updateProfileImage(
        @CurrentUser user: AuthenticatedUser,
        @RequestParam("file") file: MultipartFile,
    ): ProfileImageResponse = service.updateProfileImage(user.userId, file)

    @Authenticated
    @DeleteMapping("/me/profile-image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProfileImage(
        @CurrentUser user: AuthenticatedUser,
    ) = service.clearProfileImage(user.userId)
}
