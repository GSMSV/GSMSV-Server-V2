package com.gsmsv.api.app.service

import com.gsmsv.api.app.domain.App
import com.gsmsv.api.app.domain.AppOwnerType
import com.gsmsv.api.app.dto.CreateAppResponse
import com.gsmsv.api.app.repository.AppRepository
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.PermissionsResolver
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.ResourceType
import com.gsmsv.api.global.response.FieldErrorDetail
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.global.security.Role
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class AppCommandService(
    private val appRepository: AppRepository,
    private val userService: UserService,
    private val permissionsResolver: PermissionsResolver,
) {
    @Transactional
    fun createPersonalApp(
        user: AuthenticatedUser,
        name: String,
    ): CreateAppResponse {
        val owner = Owner.User(user.userId)
        val expiresAfter = if (user.role == Role.ADMIN) TEAM_LIFETIME else PERSONAL_LIFETIME
        return create(user, owner, name, PERSONAL_LIMIT, expiresAfter)
    }

    @Transactional
    fun createTeamApp(
        user: AuthenticatedUser,
        team: Team,
        name: String,
    ): CreateAppResponse = create(user, Owner.Team(team.id), name, TEAM_LIMIT, TEAM_LIFETIME, team.name)

    private fun create(
        user: AuthenticatedUser,
        owner: Owner,
        name: String,
        limit: Long,
        lifetime: Duration,
        teamName: String? = null,
    ): CreateAppResponse {
        val normalizedName = name.trim()
        if (normalizedName.codePointCount(0, normalizedName.length) !in 1..30) {
            throw BusinessException(
                ErrorCode.VALIDATION_FAILED,
                details = mapOf("fields" to listOf(FieldErrorDetail("name", "앱 이름은 공백 제거 후 1~30자여야 합니다."))),
            )
        }
        val ownerType = if (owner is Owner.User) AppOwnerType.USER else AppOwnerType.TEAM
        if (appRepository.countByOwnerTypeAndOwnerId(ownerType, owner.id) >= limit) {
            throw BusinessException(ErrorCode.QUOTA_EXCEEDED)
        }
        val creatorName = userService.findSummariesByIds(listOf(user.userId)).getValue(user.userId).name
        val app =
            appRepository.save(
                App(IdGenerator.resourceId(ResourceType.APP), normalizedName, owner, user.userId, Instant.now().plus(lifetime)),
            )
        val permissions = permissionsResolver.resolve(user.toActor(), app, APP_ACTIONS)
        return CreateAppResponse.from(app, teamName ?: creatorName, creatorName, permissions)
    }

    companion object {
        private const val PERSONAL_LIMIT = 2L
        private const val TEAM_LIMIT = 4L
        private val PERSONAL_LIFETIME = Duration.ofDays(14)
        private val TEAM_LIFETIME = Duration.ofDays(31)
        private val APP_ACTIONS = listOf(Action.VIEW, Action.UPDATE, Action.EXTEND, Action.DELETE)
    }
}
