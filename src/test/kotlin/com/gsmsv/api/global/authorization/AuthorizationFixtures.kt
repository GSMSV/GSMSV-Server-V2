package com.gsmsv.api.global.authorization

import com.gsmsv.api.global.grn.ResourceType

const val ME = "user-a1b2c3d4e5f6"
const val OTHER_USER = "user-b2c3d4e5f6g7"
const val TEAM = "team-c3d4e5f6g7h8"
const val OTHER_TEAM = "team-d4e5f6g7h8j9"

class FakeResource(
    override val id: String,
    override val owner: Owner,
) : OwnedResource

class FakeResourceReader(
    vararg resources: FakeResource,
    override val type: ResourceType = ResourceType.VM,
) : ServiceResourceReader<FakeResource> {
    private val resources = resources.toList()
    var findByIdCount = 0
        private set

    override fun findById(id: String): FakeResource? {
        findByIdCount++
        return resources.find { it.id == id }
    }

    override fun findAll(): List<FakeResource> = resources

    // 로더가 한 번 더 거르는지 보려고 일부러 소유자와 상관없이 전부 돌려준다
    override fun findAllByOwners(owners: Collection<Owner>): List<FakeResource> = resources
}

class FakeSubResource(
    override val id: String,
    override val parentId: String,
) : SubResource

class FakeSubResourceReader(
    vararg children: FakeSubResource,
) : SubResourceReader<FakeSubResource> {
    private val children = children.toList()

    override fun findById(id: String): FakeSubResource? = children.find { it.id == id }
}

class FakeTeam(
    override val id: String,
) : TeamResource

class FakeTeamReader(
    vararg teams: FakeTeam,
) : TeamReader<FakeTeam> {
    private val teams = teams.toList()

    override fun findById(id: String): FakeTeam? = teams.find { it.id == id }
}

/** teamId → (userId → 역할) */
class FakeTeamMembershipReader(
    private val memberships: Map<String, Map<String, TeamRole>> = emptyMap(),
) : TeamMembershipReader {
    override fun findRole(
        teamId: String,
        userId: String,
    ): TeamRole? = memberships[teamId]?.get(userId)

    override fun findMemberships(userId: String): Map<String, TeamRole> =
        memberships
            .mapNotNull { (teamId, members) -> members[userId]?.let { teamId to it } }
            .toMap()
}
