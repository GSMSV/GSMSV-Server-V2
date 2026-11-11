package com.gsmsv.api.database.provisioner

import com.gsmsv.api.global.grn.IdPattern

/**
 * SQL 식별자와 비밀번호 리터럴에 들어가는 값의 형식을 검사한다.
 *
 * CREATE USER 같은 문장은 식별자와 비밀번호를 바인딩할 수 없어 문자열로 이어 붙인다.
 * 그래서 허용하는 글자를 좁게 정해 두고, 어긋나면 SQL을 만들기 전에 거절한다 (SQL 주입 방지).
 */
internal object DatabaseIdentifier {
    private val DATABASE_ID = Regex("^db-${IdPattern.LONG}$")
    private val PASSWORD = Regex("^[A-Za-z0-9]{16,128}$")

    fun require(databaseId: String): String =
        databaseId.also {
            if (!DATABASE_ID.matches(it)) throw DatabaseProvisioningException("DB id 형식이 올바르지 않습니다.")
        }

    fun requirePassword(password: String): String =
        password.also {
            if (!PASSWORD.matches(it)) throw DatabaseProvisioningException("DB 비밀번호 형식이 올바르지 않습니다.")
        }
}
