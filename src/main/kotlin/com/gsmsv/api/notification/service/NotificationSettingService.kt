package com.gsmsv.api.notification.service

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.notification.domain.NotificationGroup
import com.gsmsv.api.notification.domain.NotificationSetting
import com.gsmsv.api.notification.domain.NotificationSettingId
import com.gsmsv.api.notification.dto.DiscordChannelResponse
import com.gsmsv.api.notification.dto.NotificationChannelsResponse
import com.gsmsv.api.notification.dto.NotificationSettingItemResponse
import com.gsmsv.api.notification.dto.NotificationSettingsResponse
import com.gsmsv.api.notification.dto.UpdateNotificationSettingItem
import com.gsmsv.api.notification.dto.UpdateNotificationSettingsResponse
import com.gsmsv.api.notification.repository.NotificationSettingRepository
import com.gsmsv.api.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationSettingService(
    private val settingRepository: NotificationSettingRepository,
    private val userService: UserService,
) {
    @Transactional(readOnly = true)
    fun get(userId: String): NotificationSettingsResponse {
        val email = userService.findSummariesByIds(listOf(userId))[userId]?.email.orEmpty()
        return NotificationSettingsResponse(
            channels = NotificationChannelsResponse(email, DiscordChannelResponse(DISCORD_CONNECTED)),
            items = items(userId),
        )
    }

    /**
     * 원하는 상태를 통째로 보낸 값으로 바꾼다. 요청에 없는 그룹은 그대로 둔다.
     * 필수 알림을 끄려는 항목이 하나라도 있으면 아무것도 바꾸지 않고 거절한다.
     */
    @Transactional
    fun update(
        userId: String,
        requested: List<UpdateNotificationSettingItem>,
    ): UpdateNotificationSettingsResponse {
        val byGroup = parseGroups(requested)
        rejectLocked(byGroup)

        val existing = settingRepository.findAllByIdUserId(userId).associateBy { it.id.group }
        byGroup.forEach { (group, item) ->
            val setting = existing[group]
            if (setting == null) {
                settingRepository.save(NotificationSetting(NotificationSettingId(userId, group), item.email, item.discord))
            } else {
                setting.change(item.email, item.discord)
            }
        }
        // 방금 저장한 값을 같은 트랜잭션에서 다시 읽는다
        settingRepository.flush()
        return UpdateNotificationSettingsResponse(items(userId))
    }

    private fun parseGroups(requested: List<UpdateNotificationSettingItem>): Map<NotificationGroup, UpdateNotificationSettingItem> {
        val parsed = requested.map { item -> (NotificationGroup.fromKey(item.group) ?: throw invalid("없는 알림 그룹입니다.")) to item }
        if (parsed.map { it.first }.toSet().size != parsed.size) throw invalid("같은 알림 그룹이 두 번 들어 있습니다.")
        return parsed.toMap()
    }

    // 일부만 조용히 무시하지 않고 요청 전체를 거절한다
    private fun rejectLocked(byGroup: Map<NotificationGroup, UpdateNotificationSettingItem>) {
        val locked = byGroup.filter { (group, item) -> group.required && !(item.email && item.discord) }.keys
        if (locked.isNotEmpty()) {
            throw BusinessException(ErrorCode.LOCKED_CATEGORY, mapOf("groups" to locked.map { it.key }))
        }
    }

    private fun items(userId: String): List<NotificationSettingItemResponse> {
        val saved = settingRepository.findAllByIdUserId(userId).associateBy { it.id.group }
        return NotificationGroup.entries.map { group ->
            val setting = saved[group]
            NotificationSettingItemResponse(
                group = group.key,
                // 필수 알림은 설정과 상관없이 항상 보낸다
                email = group.required || (setting?.email ?: NotificationSetting.DEFAULT_EMAIL),
                discord = group.required || (setting?.discord ?: NotificationSetting.DEFAULT_DISCORD),
                locked = group.required,
            )
        }
    }

    private fun invalid(message: String) = BusinessException(ErrorCode.VALIDATION_FAILED, message = message)

    companion object {
        /** Discord 계정 연결(Auth 2.x)이 생기기 전까지 연결된 사용자는 없다 */
        const val DISCORD_CONNECTED = false
    }
}
