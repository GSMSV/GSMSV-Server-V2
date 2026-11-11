package com.gsmsv.api.database.domain

import com.gsmsv.api.global.authorization.Owner
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DatabaseTest {
    @Test
    fun `새 DB는 만드는 중 상태로 시작하고 쓰기 금지가 아니다`() {
        val database = newDatabase()

        assertEquals(DatabaseStatus.CREATING, database.status)
        assertEquals(false, database.writeLocked)
        assertEquals(DatabaseIsolation.SHARED, database.isolation)
        assertEquals(Database.DEFAULT_MAX_CONNECTIONS, database.maxConnections)
    }

    @Test
    fun `소유자는 만들 때 정한 개인 또는 팀으로 돌려준다`() {
        assertEquals(Owner.User(USER_ID), newDatabase(Owner.User(USER_ID)).owner)
        assertEquals(Owner.Team(TEAM_ID), newDatabase(Owner.Team(TEAM_ID)).owner)
    }

    @Test
    fun `만들기를 마치면 사용 중이 된다`() {
        val database = newDatabase()

        database.markActive()

        assertEquals(DatabaseStatus.ACTIVE, database.status)
        assertFailsWith<IllegalStateException> { database.markActive() }
    }

    @Test
    fun `만드는 중에 실패하면 실패 상태가 되고 다시 삭제를 시작할 수 있다`() {
        val database = newDatabase()

        database.markFailed()
        database.startDeleting()

        assertEquals(DatabaseStatus.DELETING, database.status)
    }

    @Test
    fun `정지된 DB를 연장하면 사용 중으로 돌아가고 만료 시각이 늘어난다`() {
        val database = newDatabase().apply { markActive() }
        database.suspend()
        val later = EXPIRES_AT.plusSeconds(14 * 24 * 3600)

        database.extend(later)

        assertEquals(DatabaseStatus.ACTIVE, database.status)
        assertEquals(later, database.expiresAt)
    }

    @Test
    fun `만료 시각을 앞당기는 연장은 받지 않는다`() {
        val database = newDatabase().apply { markActive() }

        assertFailsWith<IllegalArgumentException> { database.extend(EXPIRES_AT.minusSeconds(1)) }
    }

    @Test
    fun `만드는 중인 DB는 정지하거나 연장할 수 없다`() {
        val database = newDatabase()

        assertFailsWith<IllegalStateException> { database.suspend() }
        assertFailsWith<IllegalStateException> { database.extend(EXPIRES_AT.plusSeconds(1)) }
    }

    @Test
    fun `삭제는 삭제 중을 거쳐 삭제 표시만 남기고 행을 지우지 않는다`() {
        val database = newDatabase().apply { markActive() }
        val at = Instant.parse("2026-11-20T00:00:00Z")

        assertFailsWith<IllegalStateException> { database.markDeleted(at) }
        database.startDeleting()
        database.markDeleted(at)

        assertEquals(DatabaseStatus.DELETED, database.status)
        assertEquals(at, database.deletedAt)
    }

    @Test
    fun `삭제를 시작하지 않은 DB에는 삭제 표시가 없다`() {
        assertNull(newDatabase().deletedAt)
    }

    @Test
    fun `쓰기 금지는 켜고 끌 수 있다`() {
        val database = newDatabase()

        database.lockWrites()
        assertEquals(true, database.writeLocked)

        database.unlockWrites()
        assertEquals(false, database.writeLocked)
    }

    @Test
    fun `이름을 바꿔도 id는 그대로다`() {
        val database = newDatabase()

        database.rename("새 이름")

        assertEquals("새 이름", database.name)
        assertEquals(DB_ID, database.id)
    }

    private fun newDatabase(owner: Owner = Owner.User(USER_ID)) =
        Database.create(
            id = DB_ID,
            name = "테스트 DB",
            owner = owner,
            engine = DatabaseEngine.MYSQL,
            createdBy = USER_ID,
            expiresAt = EXPIRES_AT,
        )

    companion object {
        private const val DB_ID = "db-k3m9x2p7q4ta"
        private const val USER_ID = "user-a1b2c3d4e5f6"
        private const val TEAM_ID = "team-c3d4e5f6g7h8"
        private val EXPIRES_AT = Instant.parse("2026-12-01T00:00:00Z")
    }
}
