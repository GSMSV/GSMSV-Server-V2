package com.gsmsv.api.app.service

import com.gsmsv.api.app.domain.App
import com.gsmsv.api.app.dto.AppEnvironmentResponse
import com.gsmsv.api.app.dto.AppSummaryResponse
import com.gsmsv.api.app.dto.CreateAppResponse
import com.gsmsv.api.app.repository.AppEnvironmentRepository
import com.gsmsv.api.app.repository.AppResourceReader
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Actor
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.PermissionsResolver
import com.gsmsv.api.global.authorization.RequestActor
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.grn.GrnParser
import com.gsmsv.api.global.response.Cursor
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.global.response.OwnerResponse
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.service.TeamService
import com.gsmsv.api.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AppQueryService(
    private val loader: ServiceResourceLoader,
    private val reader: AppResourceReader,
    private val permissionsResolver: PermissionsResolver,
    private val userService: UserService,
    private val teamService: TeamService,
    private val environmentRepository: AppEnvironmentRepository,
) {
    @Transactional(readOnly = true)
    fun listPersonal(
        actor: RequestActor,
        page: CursorPageRequest,
    ): CursorPage<AppSummaryResponse> = list(actor, Owner.User(actor.userId), page)

    @Transactional(readOnly = true)
    fun listTeam(
        actor: Actor,
        team: Team,
        page: CursorPageRequest,
    ): CursorPage<AppSummaryResponse> = list(actor, Owner.Team(team.id), page)

    private fun list(
        actor: Actor,
        owner: Owner,
        page: CursorPageRequest,
    ): CursorPage<AppSummaryResponse> {
        val cursor = page.decodedCursor()
        val rows =
            loader
                .listVisible(actor, reader, owner)
                .sortedWith(compareByDescending<App> { it.createdAt }.thenByDescending { it.id })
                .filter { cursor == null || it.createdAt < cursor.createdAt || (it.createdAt == cursor.createdAt && it.id < cursor.id) }
                .take(page.limit + 1)
        val userIds = rows.map { it.createdByUserId } + listOfNotNull(owner.id.takeIf { owner is Owner.User })
        val users = userService.findSummariesByIds(userIds)
        val ownerName =
            when (owner) {
                is Owner.User -> users[owner.id]?.name.orEmpty()
                is Owner.Team -> teamService.findById(owner.id)?.name.orEmpty()
            }
        return CursorPage.of(rows, page.limit, { Cursor(it.createdAt, it.id) }) {
            toSummary(actor, it, ownerName, users[it.createdByUserId]?.name.orEmpty())
        }
    }

    @Transactional(readOnly = true)
    fun get(
        actor: Actor,
        app: App,
    ): CreateAppResponse {
        val owner = app.owner
        val userIds = listOf(app.createdByUserId) + listOfNotNull(owner.id.takeIf { owner is Owner.User })
        val users = userService.findSummariesByIds(userIds)
        val ownerName =
            when (owner) {
                is Owner.User -> users[owner.id]?.name.orEmpty()
                is Owner.Team -> teamService.findById(owner.id)?.name.orEmpty()
            }
        return CreateAppResponse(
            grn = GrnParser.format(owner.id, app.id),
            id = app.id,
            name = app.name,
            owner = OwnerResponse.of(owner, ownerName),
            status = app.status,
            createdBy = users[app.createdByUserId]?.name.orEmpty(),
            createdAt = app.createdAt,
            updatedAt = app.updatedAt,
            expiresAt = app.expiresAt,
            permissions = permissionsResolver.resolve(actor, app, APP_ACTIONS),
            envs = environmentRepository.findAllByAppId(app.id).map { AppEnvironmentResponse(it.id, it.name) },
        )
    }

    private fun toSummary(
        actor: Actor,
        app: App,
        ownerName: String,
        creatorName: String,
    ) = AppSummaryResponse(
        grn = GrnParser.format(app.owner.id, app.id),
        id = app.id,
        name = app.name,
        owner = OwnerResponse.of(app.owner, ownerName),
        status = app.status,
        createdBy = creatorName,
        createdAt = app.createdAt,
        updatedAt = app.updatedAt,
        expiresAt = app.expiresAt,
        permissions = permissionsResolver.resolve(actor, app, APP_ACTIONS),
    )

    private companion object {
        val APP_ACTIONS = listOf(Action.VIEW, Action.UPDATE, Action.EXTEND, Action.DELETE)
    }
}
