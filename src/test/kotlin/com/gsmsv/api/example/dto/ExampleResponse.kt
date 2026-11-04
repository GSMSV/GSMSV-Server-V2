package com.gsmsv.api.example.dto

import com.gsmsv.api.example.domain.Example
import com.gsmsv.api.example.domain.ExampleStatus
import com.gsmsv.api.global.grn.GrnParser
import com.gsmsv.api.global.response.OwnerResponse
import com.gsmsv.api.global.response.ResourceResponse
import java.time.Instant

/** 서비스 자원 응답은 [ResourceResponse]를 구현하고 도메인 고유 필드를 덧붙인다. 엔티티는 controller 밖으로 내보내지 않는다 */
data class ExampleResponse(
    override val grn: String,
    override val id: String,
    override val name: String,
    override val owner: OwnerResponse,
    override val status: ExampleStatus,
    override val createdBy: String,
    override val createdAt: Instant,
    override val updatedAt: Instant,
    override val expiresAt: Instant?,
    override val permissions: Map<String, Boolean>,
) : ResourceResponse {
    companion object {
        fun of(
            example: Example,
            permissions: Map<String, Boolean>,
        ): ExampleResponse =
            ExampleResponse(
                // GRN은 DB 값으로 만든다. 요청에 들어온 문자열을 그대로 돌려주지 않는다
                grn = GrnParser.format(example.owner.id, example.id),
                id = example.id,
                name = example.name,
                owner = OwnerResponse.of(example.owner, example.ownerName),
                status = ExampleStatus.RUNNING,
                createdBy = example.createdByName,
                createdAt = example.createdAt,
                updatedAt = example.createdAt,
                expiresAt = null,
                permissions = permissions,
            )
    }
}
