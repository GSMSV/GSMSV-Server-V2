package com.gsmsv.api.notification.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationTest {
    @Test
    fun `읽음 표시를 하면 읽은 것이 된다`() {
        val notification = notification()
        assertFalse(notification.read)

        notification.markRead()

        assertTrue(notification.read)
    }

    @Test
    fun `자원 GRN과 이름은 함께 있거나 함께 없어야 한다`() {
        assertFailsWith<IllegalArgumentException> { notification(grn = "grn:x", name = null) }
        assertFailsWith<IllegalArgumentException> { notification(grn = null, name = "이름") }
    }

    @Test
    fun `그룹은 세부 종류에서 정해진다`() {
        assertEquals(NotificationGroup.USAGE, notification().group)
    }

    @Test
    fun `필수 그룹은 만료 알림뿐이다`() {
        assertEquals(listOf(NotificationGroup.EXPIRY), NotificationGroup.entries.filter { it.required })
    }

    @Test
    fun `그룹 이름으로 그룹을 찾고 없는 이름이면 null이다`() {
        assertEquals(NotificationGroup.EXPIRY, NotificationGroup.fromKey("expiry"))
        assertNull(NotificationGroup.fromKey("nope"))
        assertNotNull(NotificationCategory.DB_STORAGE_80.key)
        assertEquals("db_storage_80", NotificationCategory.DB_STORAGE_80.key)
    }

    @Test
    fun `설정을 바꾸면 이메일과 Discord 값이 바뀐다`() {
        val setting = NotificationSetting(NotificationSettingId("user-a1b2c3d4e5f6", NotificationGroup.USAGE), true, false)

        setting.change(email = false, discord = true)

        assertFalse(setting.email)
        assertTrue(setting.discord)
    }

    private fun notification(
        grn: String? = null,
        name: String? = null,
    ) = Notification(
        id = "ntf-4k8m2q9t",
        userId = "user-a1b2c3d4e5f6",
        kind = NotificationKind.WARNING,
        category = NotificationCategory.DB_STORAGE_80,
        title = "제목",
        body = "본문",
        resourceGrn = grn,
        resourceName = name,
    )
}
