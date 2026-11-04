package com.gsmsv.api.global.authorization

/**
 * DB에 저장된 서비스 자원의 소유자. 개인·팀 2종뿐이고, 자원을 만든 뒤에는 바꾸지 않는다.
 *
 * [id]는 접두사를 포함한 전체 id다 (예: `user-a1b2c3d4e5f6`).
 */
sealed interface Owner {
    val id: String

    data class User(
        override val id: String,
    ) : Owner

    data class Team(
        override val id: String,
    ) : Owner
}
