package com.gsmsv.api.vm.config

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * VM을 만들 때 고를 수 있는 운영체제 목록 (Contracts-V2 `openapi/vm.yaml` 4.1, `policies/quota.md` 5장).
 *
 * 운영체제 추가(31번)는 드물고 템플릿과 함께 운영이 바꾸는 값이라 DB가 아니라 설정에 둔다. 순서는 화면에 보여 줄 순서다.
 */
@Validated
@ConfigurationProperties("gsmsv.vm")
data class VmImageProperties(
    @field:NotEmpty @field:Valid val images: List<Image>,
) {
    init {
        val duplicated = images.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicated.isEmpty()) { "gsmsv.vm.images의 id가 겹친다: $duplicated" }
    }

    data class Image(
        // 내부 API(VM 만들기)의 `image`로 그대로 넘기므로 같은 형식을 쓴다
        @field:Pattern(regexp = "^[a-z0-9]+([.-][a-z0-9]+)*$") @field:Size(max = 64) val id: String,
        @field:NotBlank val name: String,
        /** 운영체제 공식 최소 RAM을 1 GiB 단위로 올림한 값 */
        @field:Positive val minRamGib: Int,
        /** 운영체제 공식 최소 저장 공간을 1 GiB 단위로 올림한 값 */
        @field:Positive val minSsdGib: Int,
        /** 템플릿 디스크 크기(GiB). Infra가 정하는 값이고, 모르면 비워 둔다 */
        @field:Positive val templateDiskGib: Int? = null,
    )
}
