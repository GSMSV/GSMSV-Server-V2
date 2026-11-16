package com.gsmsv.api.database.domain

/** 1차는 MySQL, PostgreSQL은 두 번째 단계다 (24번 DBaaS). */
enum class DatabaseEngine {
    MYSQL,
    POSTGRESQL,
}
