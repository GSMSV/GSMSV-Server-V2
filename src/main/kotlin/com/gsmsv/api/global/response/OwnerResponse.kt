package com.gsmsv.api.global.response

import com.fasterxml.jackson.annotation.JsonValue
import com.gsmsv.api.global.authorization.Owner

/** 자원 응답의 `owner` 객체. [id]는 접두사를 포함한 전체 id다. */
data class OwnerResponse(
    val type: OwnerType,
    val id: String,
    val name: String,
) {
    companion object {
        fun of(
            owner: Owner,
            name: String,
        ): OwnerResponse =
            when (owner) {
                is Owner.User -> OwnerResponse(OwnerType.USER, owner.id, name)
                is Owner.Team -> OwnerResponse(OwnerType.TEAM, owner.id, name)
            }
    }
}

enum class OwnerType(
    @get:JsonValue val value: String,
) {
    USER("user"),
    TEAM("team"),
}
