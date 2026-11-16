package com.gsmsv.api.database.provisioner

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeDatabaseProvisionerTest {
    private val provisioner = FakeDatabaseProvisioner()
    private val id = "db-0123456789ab"
    private val password = "A".repeat(32)

    @Test
    fun `만들면 비밀번호와 동시 연결 수가 기억된다`() {
        provisioner.create(id, password, 10)

        val account = assertNotNull(provisioner.account(id))
        assertEquals(10, account.maxConnections)
        assertFalse(account.locked)
    }

    @Test
    fun `만들다 실패하면 아무것도 남지 않는다`() {
        provisioner.failNextCreate = true

        assertFailsWith<DatabaseProvisioningException> { provisioner.create(id, password, 10) }

        assertNull(provisioner.account(id))
    }

    @Test
    fun `비밀번호 교체와 잠금 해제가 반영된다`() {
        provisioner.create(id, password, 10)

        provisioner.rotatePassword(id, "B".repeat(32))
        provisioner.lock(id)
        assertTrue(assertNotNull(provisioner.account(id)).locked)
        provisioner.unlock(id)

        val account = assertNotNull(provisioner.account(id))
        assertEquals("B".repeat(32), account.password)
        assertFalse(account.locked)
    }

    @Test
    fun `지운 뒤에는 없고 다시 지워도 오류가 아니다`() {
        provisioner.create(id, password, 10)

        provisioner.drop(id)
        provisioner.drop(id)

        assertNull(provisioner.account(id))
    }

    @Test
    fun `없는 database를 바꾸면 실패한다`() {
        assertFailsWith<DatabaseProvisioningException> { provisioner.lock(id) }
    }

    @Test
    fun `형식이 틀린 id와 비밀번호는 거절한다`() {
        assertFailsWith<DatabaseProvisioningException> { provisioner.create("db-x`; DROP", password, 10) }
        assertFailsWith<DatabaseProvisioningException> { provisioner.create(id, "short'; --", 10) }
    }
}
