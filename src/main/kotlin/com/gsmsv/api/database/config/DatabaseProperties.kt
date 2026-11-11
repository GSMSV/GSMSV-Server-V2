package com.gsmsv.api.database.config

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * DB 자원 설정. 사용자에게 보여 주는 접속 주소와 소유자별 용량 한도 (Contracts-V2 `services/database.md` 7장).
 *
 * 접속 주소는 노드를 드러내지 않는 값이어야 하고 운영 값이라 코드에 두지 않는다. 환경 변수로 덮어쓴다.
 * **(미정)** 호스트 이름의 형식은 인프라 담당이 정한다.
 */
@Validated
@ConfigurationProperties("gsmsv.database")
data class DatabaseProperties(
    @field:Valid val connection: Connection,
    @field:Valid val storageLimit: StorageLimit,
) {
    data class Connection(
        @field:NotBlank val host: String,
        @field:Positive val port: Int = 3306,
        // 학생 VM에서는 TLS가 필수다 (24번 DBaaS)
        val tls: Boolean = true,
    )

    /** 공유형 용량 한도. 소유자별 합계이고 단위는 GiB다 */
    data class StorageLimit(
        @field:Positive val personalGib: Double,
        @field:Positive val teamGib: Double,
    )
}
