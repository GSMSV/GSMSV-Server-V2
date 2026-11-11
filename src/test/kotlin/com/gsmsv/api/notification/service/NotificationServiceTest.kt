package com.gsmsv.api.notification.service

import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.notification.domain.Notification
import com.gsmsv.api.notification.domain.NotificationCategory
import com.gsmsv.api.notification.repository.NotificationRepository
import com.gsmsv.api.team.service.TeamService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationServiceTest {
    private val notificationRepository = mockk<NotificationRepository>(relaxed = true)
    private val teamService = mockk<TeamService>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = NotificationService(notificationRepository, teamService, eventPublisher)

    private val message =
        NotificationMessage(
            category = NotificationCategory.DB_STORAGE_80,
            title = "DB 용량이 80%에 도달했습니다",
            body = "주문 서비스 DB가 한도의 80%를 썼습니다.",
            resource = NotificationResource("grn:team-4p8w2k9m1xza:db-k3m9x2p7q4ta", "주문 서비스 DB"),
        )

    @Test
    fun `사용자 앞으로 보내면 그 사용자의 알림 1건을 저장한다`() {
        val saved = slot<Notification>()
        every { notificationRepository.save(capture(saved)) } answers { saved.captured }

        service.send(Owner.User(USER_ID), message)

        assertEquals(USER_ID, saved.captured.userId)
        assertEquals(NotificationCategory.DB_STORAGE_80, saved.captured.category)
        assertEquals(message.category.kind, saved.captured.kind)
        assertEquals("주문 서비스 DB", saved.captured.resourceName)
        assertTrue(saved.captured.id.startsWith("ntf-"))
        assertEquals(false, saved.captured.read)
    }

    @Test
    fun `팀 앞으로 보내면 팀원 전원에게 알림을 저장한다`() {
        every { teamService.findMemberUserIds(TEAM_ID) } returns listOf(USER_ID, OTHER_USER_ID)
        val saved = mutableListOf<Notification>()
        every { notificationRepository.save(capture(saved)) } answers { firstArg() }

        service.send(Owner.Team(TEAM_ID), message)

        assertEquals(listOf(USER_ID, OTHER_USER_ID), saved.map { it.userId })
    }

    @Test
    fun `팀원이 없으면 아무것도 저장하지 않고 이벤트도 올리지 않는다`() {
        every { teamService.findMemberUserIds(TEAM_ID) } returns emptyList()

        service.send(Owner.Team(TEAM_ID), message)

        verify(exactly = 0) { notificationRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `받는 사람마다 외부 채널 발송용 이벤트를 한 번에 올린다`() {
        every { teamService.findMemberUserIds(TEAM_ID) } returns listOf(USER_ID, OTHER_USER_ID)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val event = slot<Any>()
        every { eventPublisher.publishEvent(capture(event)) } returns Unit

        service.send(Owner.Team(TEAM_ID), message)

        val created = event.captured as NotificationCreatedEvent
        assertEquals(listOf(USER_ID, OTHER_USER_ID), created.deliveries.map { it.userId })
    }

    @Test
    fun `보관 상한을 넘으면 오래된 알림부터 지운다`() {
        val oldest = listOf(notification("ntf-aaaaaaaa"), notification("ntf-bbbbbbbb"))
        every { notificationRepository.save(any()) } answers { firstArg() }
        every { notificationRepository.countByUserId(USER_ID) } returns NotificationService.MAX_PER_USER + 2L
        every { notificationRepository.findByUserIdOrderByCreatedAtAsc(USER_ID, PageRequest.of(0, 2)) } returns oldest

        service.send(Owner.User(USER_ID), message)

        verify { notificationRepository.deleteAll(oldest) }
    }

    @Test
    fun `보관 상한 이내면 지우지 않는다`() {
        every { notificationRepository.save(any()) } answers { firstArg() }
        every { notificationRepository.countByUserId(USER_ID) } returns NotificationService.MAX_PER_USER.toLong()

        service.send(Owner.User(USER_ID), message)

        verify(exactly = 0) { notificationRepository.deleteAll(any<Iterable<Notification>>()) }
    }

    @Test
    fun `자원이 없는 알림도 보낸다`() {
        val saved = slot<Notification>()
        every { notificationRepository.save(capture(saved)) } answers { saved.captured }

        service.send(Owner.User(USER_ID), message.copy(resource = null))

        assertNull(saved.captured.resourceGrn)
        assertNull(saved.captured.resourceName)
    }

    private fun notification(id: String) = Notification(id, USER_ID, message.category.kind, message.category, message.title, message.body)

    companion object {
        private const val USER_ID = "user-a1b2c3d4e5f6"
        private const val OTHER_USER_ID = "user-b2c3d4e5f6g7"
        private const val TEAM_ID = "team-4p8w2k9m1xza"
    }
}
