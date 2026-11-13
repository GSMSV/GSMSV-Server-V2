package com.gsmsv.api.database.service

import com.gsmsv.api.database.config.DatabaseProperties
import com.gsmsv.api.database.domain.Database
import com.gsmsv.api.database.dto.CreatedByResponse
import com.gsmsv.api.database.dto.DatabaseConnectionResponse
import com.gsmsv.api.database.dto.DatabaseDetailStorageResponse
import com.gsmsv.api.database.dto.DatabaseResponse
import com.gsmsv.api.database.dto.DatabaseStorageResponse
import com.gsmsv.api.database.dto.DatabaseSummaryResponse
import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Actor
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.PermissionsResolver
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.grn.GrnParser
import com.gsmsv.api.global.response.Cursor
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.global.response.OwnerResponse
import com.gsmsv.api.team.service.TeamService
import com.gsmsv.api.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * DB 목록·상세 (7.1·7.4). 상세는 로더가 돌려준 [Database]를 받고, 목록은 `listVisible`로만 꺼낸다.
 * 권한 판단은 로더에서 끝났으므로 여기서 소유자·역할을 다시 비교하지 않는다.
 */
@Service
class DatabaseApiService(
    private val loader: ServiceResourceLoader,
    private val reader: DatabaseResourceReader,
    private val permissionsResolver: PermissionsResolver,
    private val userService: UserService,
    private val teamService: TeamService,
    private val usageReader: DatabaseUsageReader,
    private val properties: DatabaseProperties,
) {
    @Transactional(readOnly = true)
    fun list(
        actor: Actor,
        owner: Owner?,
        page: CursorPageRequest,
    ): CursorPage<DatabaseSummaryResponse> {
        val cursor = page.decodedCursor()
        val rows =
            loader
                .listVisible(actor, reader, owner)
                .sortedWith(compareByDescending<Database> { it.createdAt }.thenByDescending { it.id })
                .filter { cursor == null || isAfter(it, cursor) }
                .take(page.limit + 1)
        return CursorPage.of(rows, page.limit, { Cursor(it.createdAt, it.id) }, ::toSummary)
    }

    @Transactional(readOnly = true)
    fun get(
        actor: Actor,
        database: Database,
    ): DatabaseResponse {
        val owner = database.owner
        val createdByName = userName(database.createdBy)
        return DatabaseResponse(
            grn = grnOf(database),
            id = database.id,
            name = database.name,
            owner = OwnerResponse.of(owner, ownerName(owner)),
            engine = database.engine.name.lowercase(),
            isolation = database.isolation.name.lowercase(),
            status = database.status.name.lowercase(),
            writeLocked = database.writeLocked,
            connection =
                DatabaseConnectionResponse(
                    host = properties.connection.host,
                    port = properties.connection.port,
                    // 서버 쪽 database·계정 이름은 db id 그대로다 (사용자가 지은 이름은 쓰지 않는다)
                    database = database.id,
                    username = database.id,
                    tls = properties.connection.tls,
                ),
            storage =
                DatabaseDetailStorageResponse(
                    usedGb = usageReader.usedGib(database.id),
                    ownerTotalUsedGb = usageReader.ownerUsedGib(owner),
                    ownerLimitGb = limitGib(owner),
                ),
            maxConnections = database.maxConnections,
            createdBy = CreatedByResponse(createdByName),
            createdAt = database.createdAt,
            updatedAt = database.updatedAt,
            expiresAt = database.expiresAt,
            // 명세의 db.* 키는 동작 이름이 확정될 때까지 서버의 Action 소문자 키로 내보낸다 (AGENTS.md 5.3)
            permissions = permissionsResolver.resolve(actor, database, PERMISSION_ACTIONS),
        )
    }

    private fun toSummary(database: Database) =
        DatabaseSummaryResponse(
            grn = grnOf(database),
            name = database.name,
            engine = database.engine.name.lowercase(),
            isolation = database.isolation.name.lowercase(),
            status = database.status.name.lowercase(),
            writeLocked = database.writeLocked,
            storage = DatabaseStorageResponse(usageReader.usedGib(database.id)),
            expiresAt = database.expiresAt,
        )

    private fun grnOf(database: Database) = GrnParser.format(database.owner.id, database.id)

    private fun userName(userId: String): String = userService.findSummariesByIds(listOf(userId))[userId]?.name.orEmpty()

    private fun ownerName(owner: Owner): String =
        when (owner) {
            is Owner.User -> userName(owner.id)
            is Owner.Team -> teamService.findById(owner.id)?.name.orEmpty()
        }

    private fun limitGib(owner: Owner): Double =
        when (owner) {
            is Owner.User -> properties.storageLimit.personalGib
            is Owner.Team -> properties.storageLimit.teamGib
        }

    private fun isAfter(
        database: Database,
        cursor: Cursor,
    ): Boolean = database.createdAt < cursor.createdAt || (database.createdAt == cursor.createdAt && database.id < cursor.id)

    private companion object {
        val PERMISSION_ACTIONS = listOf(Action.DELETE, Action.EXTEND, Action.VIEW_ACCESS_INFO, Action.ROTATE_PASSWORD)
    }
}
