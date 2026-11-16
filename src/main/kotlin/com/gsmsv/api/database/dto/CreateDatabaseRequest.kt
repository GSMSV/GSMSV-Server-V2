package com.gsmsv.api.database.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * `name`은 앞뒤 공백을 지운 뒤 1~30자다 (공백으로만 이뤄진 값은 거절한다).
 * `engine`은 1차에서 `mysql`만 받는다. PostgreSQL은 두 번째 단계다 (24번 DBaaS).
 */
data class CreateDatabaseRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^\\s*(\\S|\\S.{0,28}\\S)\\s*$", message = "DB 이름은 공백 제거 후 1~30자여야 합니다.")
    val name: String,
    @field:NotBlank
    @field:Pattern(regexp = "mysql", message = "지금은 mysql만 만들 수 있습니다.")
    val engine: String,
)
