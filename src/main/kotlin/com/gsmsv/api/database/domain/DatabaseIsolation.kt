package com.gsmsv.api.database.domain

/** 공유형은 DB 서버를 여럿이 나눠 쓰고, 전용형은 팀 하나가 서버를 통째로 쓴다. */
enum class DatabaseIsolation {
    SHARED,
    DEDICATED,
}
