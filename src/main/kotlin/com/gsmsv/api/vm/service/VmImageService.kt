package com.gsmsv.api.vm.service

import com.gsmsv.api.vm.config.VmImageProperties
import org.springframework.stereotype.Service

/** 운영체제 목록 (4.1). VM 생성(4.3·4.4)도 [find]로 이미지 id와 최소 사양을 검사한다 */
@Service
class VmImageService(
    properties: VmImageProperties,
) {
    private val images: List<VmImage> = properties.images.map { it.toVmImage() }

    fun list(): List<VmImage> = images

    fun find(id: String): VmImage? = images.firstOrNull { it.id == id }

    private fun VmImageProperties.Image.toVmImage() =
        VmImage(
            id = id,
            name = name,
            minVcpu = MIN_VCPU,
            minRamGib = minRamGib,
            // Proxmox는 디스크를 줄일 수 없어서 템플릿 디스크보다 작게 만들 수 없다 (Contracts-V2 `policies/quota.md` 5장)
            minSsdGib = maxOf(minSsdGib, templateDiskGib ?: 0),
        )

    private companion object {
        const val MIN_VCPU = 1
    }
}
