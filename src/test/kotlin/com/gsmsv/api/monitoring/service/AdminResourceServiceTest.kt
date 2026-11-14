package com.gsmsv.api.monitoring.service

import com.gsmsv.api.app.domain.App
import com.gsmsv.api.app.repository.AppResourceReader
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.domain.DatabaseEngine
import com.gsmsv.api.database.service.DatabaseResourceReader
import com.gsmsv.api.global.authorization.AdminActor
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.grn.ResourceType
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.team.service.TeamService
import com.gsmsv.api.user.dto.UserSummary
import com.gsmsv.api.user.service.UserService
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.service.VmResourceReader
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminResourceServiceTest {
    private val loader = mockk<ServiceResourceLoader>()
    private val vmReader = mockk<VmResourceReader>()
    private val databaseReader = mockk<DatabaseResourceReader>()
    private val appReader = mockk<AppResourceReader>()
    private val userService = mockk<UserService>()
    private val teamService = mockk<TeamService>()
    private val service = AdminResourceService(loader, vmReader, databaseReader, appReader, userService, teamService)
    private val actor = AdminActor(ADMIN)

    @Test
    fun `VM·DB·앱을 모아 최근 순으로 주고 limit보다 많으면 다음 커서를 준다`() {
        stubLists(
            vms = listOf(vm("vm-aaaaaaaaaaaa", at(1))),
            databases = listOf(database("db-bbbbbbbbbbbb", at(3))),
            apps = listOf(app("app-cccccccccccc", at(2))),
        )
        stubEmails()

        val page = service.list(actor, AdminResourceFilter(), CursorPageRequest(limit = 2))

        assertEquals(listOf("db", "app"), page.items.map { it.type })
        assertNotNull(page.nextCursor)

        val next = service.list(actor, AdminResourceFilter(), CursorPageRequest(cursor = page.nextCursor, limit = 2))
        assertEquals(listOf("vm"), next.items.map { it.type })
        assertNull(next.nextCursor)
    }

    @Test
    fun `항목은 DB 값으로 채우고 VM 노드·vmid·전원 상태는 비워 둔다`() {
        stubLists(vms = listOf(vm("vm-k3m9x2p7q4ta", at(1))))
        stubEmails()

        val item = service.list(actor, AdminResourceFilter(), CursorPageRequest()).items.single()

        assertEquals("grn:$OWNER_USER:vm-k3m9x2p7q4ta", item.grn)
        assertEquals("vm", item.type)
        assertEquals("웹 서버", item.name)
        assertEquals("creating", item.status)
        assertEquals("owner@gsm.hs.kr", item.ownerEmail)
        assertNull(item.node)
        assertNull(item.vmid)
        assertNull(item.powerState)
    }

    @Test
    fun `팀 소유 자원의 owner_email은 팀장 이메일이다`() {
        stubLists(databases = listOf(database("db-k3m9x2p7q4ta", at(1), Owner.Team(TEAM))))
        every { teamService.findLeaderUserIds(listOf(TEAM)) } returns mapOf(TEAM to LEADER)
        every { userService.findSummariesByIds(listOf(LEADER)) } returns
            mapOf(LEADER to UserSummary(LEADER, "팀장", "leader@gsm.hs.kr"))

        val item = service.list(actor, AdminResourceFilter(), CursorPageRequest()).items.single()

        assertEquals("leader@gsm.hs.kr", item.ownerEmail)
    }

    @Test
    fun `type을 주면 그 종류만 listVisible로 꺼낸다`() {
        stubLists(databases = listOf(database("db-k3m9x2p7q4ta", at(1))))
        stubEmails()

        val page = service.list(actor, AdminResourceFilter(type = ResourceType.DATABASE), CursorPageRequest())

        assertEquals(listOf("db"), page.items.map { it.type })
        verify(exactly = 0) { loader.listVisible(actor, vmReader, any()) }
        verify(exactly = 0) { loader.listVisible(actor, appReader, any()) }
    }

    @Test
    fun `owner를 주면 listVisible에 그 소유자를 넘긴다`() {
        val owner = Owner.Team(TEAM)
        every { loader.listVisible(actor, vmReader, owner) } returns emptyList()
        every { loader.listVisible(actor, databaseReader, owner) } returns emptyList()
        every { loader.listVisible(actor, appReader, owner) } returns emptyList()
        every { teamService.findLeaderUserIds(any()) } returns emptyMap()

        val page = service.list(actor, AdminResourceFilter(owner = owner), CursorPageRequest())

        assertTrue(page.items.isEmpty())
        verify { loader.listVisible(actor, vmReader, owner) }
    }

    @Test
    fun `노드를 저장하지 않으므로 node로 거르면 빈 목록이다`() {
        every { teamService.findLeaderUserIds(any()) } returns emptyMap()

        val page = service.list(actor, AdminResourceFilter(node = "node1"), CursorPageRequest())

        assertTrue(page.items.isEmpty())
        verify(exactly = 0) { loader.listVisible(any(), vmReader, any()) }
    }

    @Test
    fun `fn은 도메인이 없어 빈 목록이다`() {
        every { teamService.findLeaderUserIds(any()) } returns emptyMap()

        val page = service.list(actor, AdminResourceFilter(type = ResourceType.FUNCTION), CursorPageRequest())

        assertTrue(page.items.isEmpty())
    }

    private fun stubLists(
        vms: List<Vm> = emptyList(),
        databases: List<Database> = emptyList(),
        apps: List<App> = emptyList(),
    ) {
        every { loader.listVisible(actor, vmReader, null) } returns vms
        every { loader.listVisible(actor, databaseReader, null) } returns databases
        every { loader.listVisible(actor, appReader, null) } returns apps
    }

    private fun stubEmails() {
        every { teamService.findLeaderUserIds(any()) } returns emptyMap()
        every { userService.findSummariesByIds(any()) } returns
            mapOf(OWNER_USER to UserSummary(OWNER_USER, "홍길동", "owner@gsm.hs.kr"))
    }

    private fun at(day: Int) = Instant.parse("2026-11-0${day}T00:00:00Z")

    private fun vm(
        id: String,
        createdAt: Instant,
    ) = Vm(
        id = id,
        initialName = "웹 서버",
        purpose = null,
        ownerType = Vm.OwnerType.USER,
        ownerId = OWNER_USER,
        image = "ubuntu-24.04",
        vcpu = 1,
        ramGb = 1,
        ssdGb = 10,
        initialUsername = "gsmsv",
        initialPasswordEncrypted = "encrypted",
        createdBy = OWNER_USER,
        initialExpiresAt = EXPIRES_AT,
        initialCreatedAt = createdAt,
    )

    private fun database(
        id: String,
        createdAt: Instant,
        owner: Owner = Owner.User(OWNER_USER),
    ) = Database(
        id = id,
        initialName = "주문 DB",
        ownerType =
            when (owner) {
                is Owner.User -> Database.OwnerType.USER
                is Owner.Team -> Database.OwnerType.TEAM
            },
        ownerId = owner.id,
        engine = DatabaseEngine.MYSQL,
        createdBy = OWNER_USER,
        initialExpiresAt = EXPIRES_AT,
        initialCreatedAt = createdAt,
    )

    private fun app(
        id: String,
        createdAt: Instant,
    ) = App(id, "주문 앱", Owner.User(OWNER_USER), OWNER_USER, EXPIRES_AT, createdAt)

    private companion object {
        const val ADMIN = "user-a1b2c3d4e5f6"
        const val OWNER_USER = "user-b2c3d4e5f6g7"
        const val LEADER = "user-c3d4e5f6g7h8"
        const val TEAM = "team-c3d4e5f6g7h8"
        val EXPIRES_AT: Instant = Instant.parse("2027-02-28T00:00:00Z")
    }
}
