package com.gsmsv.api.database.provisioner

import java.util.concurrent.ConcurrentHashMap

/**
 * 공유 DB 서버 없이 개발·테스트에서 쓰는 모의 구현. 메모리에만 기억하고 테스트가 상태를 들여다볼 수 있다.
 */
class FakeDatabaseProvisioner : SharedDatabaseProvisioner {
    private val accounts = ConcurrentHashMap<String, Account>()

    /** 다음 [create]를 중간에 실패시킨다. 정리 동작을 확인하는 테스트용 */
    @Volatile
    var failNextCreate: Boolean = false

    data class Account(
        val password: String,
        val maxConnections: Int,
        val locked: Boolean,
    )

    fun account(databaseId: String): Account? = accounts[databaseId]

    override fun create(
        databaseId: String,
        password: String,
        maxConnections: Int,
    ) {
        DatabaseIdentifier.require(databaseId)
        DatabaseIdentifier.requirePassword(password)
        if (failNextCreate) {
            failNextCreate = false
            // 실제 구현처럼 중간에 실패하면 남은 것이 없어야 한다
            throw DatabaseProvisioningException("모의 구현에서 만들기를 실패시켰습니다.")
        }
        val previous = accounts.putIfAbsent(databaseId, Account(password, maxConnections, locked = false))
        if (previous != null) throw DatabaseProvisioningException("이미 있는 database입니다.")
    }

    override fun rotatePassword(
        databaseId: String,
        newPassword: String,
    ) {
        update(databaseId) { it.copy(password = newPassword) }
    }

    override fun lock(databaseId: String) {
        update(databaseId) { it.copy(locked = true) }
    }

    override fun unlock(databaseId: String) {
        update(databaseId) { it.copy(locked = false) }
    }

    override fun drop(databaseId: String) {
        DatabaseIdentifier.require(databaseId)
        accounts.remove(databaseId)
    }

    private fun update(
        databaseId: String,
        change: (Account) -> Account,
    ) {
        DatabaseIdentifier.require(databaseId)
        val updated = accounts.computeIfPresent(databaseId) { _, account -> change(account) }
        if (updated == null) throw DatabaseProvisioningException("없는 database입니다.")
    }
}
