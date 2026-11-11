package com.gsmsv.api.notification.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class NotificationSettingItemResponse(
    val group: String,
    val email: Boolean,
    val discord: Boolean,
    val locked: Boolean,
)

data class DiscordChannelResponse(
    val connected: Boolean,
)

data class NotificationChannelsResponse(
    val email: String,
    val discord: DiscordChannelResponse,
)

data class NotificationSettingsResponse(
    val channels: NotificationChannelsResponse,
    val items: List<NotificationSettingItemResponse>,
)

data class UpdateNotificationSettingsResponse(
    val items: List<NotificationSettingItemResponse>,
)

data class UpdateNotificationSettingItem(
    @field:NotBlank
    val group: String,
    val email: Boolean,
    val discord: Boolean,
)

data class UpdateNotificationSettingsRequest(
    @field:NotEmpty
    @field:Valid
    val items: List<UpdateNotificationSettingItem>,
)
