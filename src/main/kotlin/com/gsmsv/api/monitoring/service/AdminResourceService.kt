package com.gsmsv.api.monitoring.service

import com.gsmsv.api.app.repository.AppResourceReader
import com.gsmsv.api.database.service.DatabaseResourceReader
import com.gsmsv.api.global.authorization.Actor
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceLoader
import com.gsmsv.api.global.grn.GrnParser
import com.gsmsv.api.global.grn.ResourceType
import com.gsmsv.api.global.response.Cursor
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.monitoring.dto.AdminResourceResponse
import com.gsmsv.api.team.service.TeamService
import com.gsmsv.api.user.service.UserService
import com.gsmsv.api.vm.service.VmResourceReader
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 전체 자원 목록의 거르기 조건. [type]이 null이면 모든 종류다 */
data class AdminResourceFilter(
    val type: ResourceType? = null,
    val node: String? = null,
    val owner: Owner? = null,
)

/**
 * 관리자 전체 자원 목록 (11.14). DB 기준으로 VM·DB·앱을 모아 `created_at`·`id` 내림차순 커서 페이지로 준다.
 *
 * 관리자 판단은 controller가 [com.gsmsv.api.global.authorization.PlatformLoader]로 끝낸다.
 * 자원은 종류마다 `listVisible`로만 꺼내고, 여기서 소유자·역할을 다시 비교하지 않는다.
 */
@Service
class AdminResourceService(
    private val loader: ServiceResourceLoader,
    private val vmReader: VmResourceReader,
    private val databaseReader: DatabaseResourceReader,
    private val appReader: AppResourceReader,
    private val userService: UserService,
    private val teamService: TeamService,
) {
    @Transactional(readOnly = true)
    fun list(
        actor: Actor,
        filter: AdminResourceFilter,
        page: CursorPageRequest,
    ): CursorPage<AdminResourceResponse> {
        val cursor = page.decodedCursor()
        val rows =
            collect(actor, filter)
                .sortedWith(compareByDescending<Row> { it.createdAt }.thenByDescending { it.id })
                .filter { cursor == null || it.isAfter(cursor) }
                .take(page.limit + 1)
        val emails = ownerEmails(rows.take(page.limit).map { it.owner })
        return CursorPage.of(rows, page.limit, { Cursor(it.createdAt, it.id) }) { it.toResponse(emails) }
    }

    private fun collect(
        actor: Actor,
        filter: AdminResourceFilter,
    ): List<Row> {
        // VM 테이블에 노드를 저장하지 않으므로 노드로 거르면 맞는 자원이 없다. 저장하게 되면 VM만 노드로 거른다
        if (filter.node != null) return emptyList()
        val types = filter.type?.let(::listOf) ?: ResourceType.entries
        return types.flatMap { rowsOf(actor, it, filter.owner) }
    }

    private fun rowsOf(
        actor: Actor,
        type: ResourceType,
        owner: Owner?,
    ): List<Row> =
        when (type) {
            ResourceType.VM -> {
                loader.listVisible(actor, vmReader, owner).map {
                    Row(it.id, it.owner, type, it.name, it.status.name.lowercase(), it.createdAt)
                }
            }

            ResourceType.DATABASE -> {
                loader.listVisible(actor, databaseReader, owner).map {
                    Row(it.id, it.owner, type, it.name, it.status.name.lowercase(), it.createdAt)
                }
            }

            ResourceType.APP -> {
                loader.listVisible(actor, appReader, owner).map {
                    Row(it.id, it.owner, type, it.name, it.status, it.createdAt)
                }
            }

            // 서버리스 함수 도메인이 아직 없다. 생기면 같은 방식으로 붙인다
            ResourceType.FUNCTION -> {
                emptyList()
            }
        }

    /** 소유자 id → 이메일. 팀 소유 자원은 팀장 이메일을 쓴다 */
    private fun ownerEmails(owners: Collection<Owner>): Map<String, String> {
        val leaders = teamService.findLeaderUserIds(owners.filterIsInstance<Owner.Team>().map { it.id }.distinct())
        val userIds = (owners.filterIsInstance<Owner.User>().map { it.id } + leaders.values).distinct()
        val emails = if (userIds.isEmpty()) emptyMap() else userService.findSummariesByIds(userIds).mapValues { it.value.email }
        return owners.associate { owner ->
            val userId =
                when (owner) {
                    is Owner.User -> owner.id
                    is Owner.Team -> leaders[owner.id]
                }
            owner.id to userId?.let(emails::get).orEmpty()
        }
    }

    private data class Row(
        val id: String,
        val owner: Owner,
        val type: ResourceType,
        val name: String,
        val status: String,
        val createdAt: Instant,
    ) {
        fun isAfter(cursor: Cursor): Boolean = createdAt < cursor.createdAt || (createdAt == cursor.createdAt && id < cursor.id)

        // node·vmid는 DB에 없고, power_state는 Go 상태 캐시(34번 제안)가 생기면 VM에만 채운다
        fun toResponse(emails: Map<String, String>) =
            AdminResourceResponse(
                grn = GrnParser.format(owner.id, id),
                type = type.prefix,
                name = name,
                node = null,
                vmid = null,
                ownerEmail = emails[owner.id].orEmpty(),
                status = status,
                powerState = null,
            )
    }
}
