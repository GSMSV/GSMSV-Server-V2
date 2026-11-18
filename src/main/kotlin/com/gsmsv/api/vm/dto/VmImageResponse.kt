package com.gsmsv.api.vm.dto

import com.gsmsv.api.vm.service.VmImage

data class VmImageListResponse(
    val items: List<VmImageResponse>,
)

data class VmImageResponse(
    val id: String,
    val name: String,
    val min: VmImageMinResponse,
) {
    companion object {
        fun from(image: VmImage) =
            VmImageResponse(
                id = image.id,
                name = image.name,
                min = VmImageMinResponse(vcpu = image.minVcpu, ramGb = image.minRamGib, ssdGb = image.minSsdGib),
            )
    }
}

/** 칸 이름은 `_gb`지만 값은 GiB다 (Contracts-V2 `policies/quota.md` 5장) */
data class VmImageMinResponse(
    val vcpu: Int,
    val ramGb: Int,
    val ssdGb: Int,
)
