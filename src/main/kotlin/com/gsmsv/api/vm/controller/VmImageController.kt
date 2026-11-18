package com.gsmsv.api.vm.controller

import com.gsmsv.api.global.authorization.Authenticated
import com.gsmsv.api.vm.dto.VmImageListResponse
import com.gsmsv.api.vm.dto.VmImageResponse
import com.gsmsv.api.vm.service.VmImageService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 고를 수 있는 운영체제 목록 API (4.1). 자원이 없는 조회라 로더 없이 로그인한 사용자에게 연다 */
@RestController
@RequestMapping("/api/v2/vm-images")
class VmImageController(
    private val vmImageService: VmImageService,
) {
    @Authenticated
    @GetMapping
    fun list(): VmImageListResponse = VmImageListResponse(vmImageService.list().map(VmImageResponse::from))
}
