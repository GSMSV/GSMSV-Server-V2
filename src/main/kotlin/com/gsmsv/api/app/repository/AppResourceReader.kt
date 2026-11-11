package com.gsmsv.api.app.repository

import com.gsmsv.api.app.domain.App
import com.gsmsv.api.app.domain.AppOwnerType
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceReader
import com.gsmsv.api.global.grn.ResourceType
import org.springframework.stereotype.Repository

@Repository
class AppResourceReader(
    private val repository: AppRepository,
) : ServiceResourceReader<App> {
    override val type: ResourceType = ResourceType.APP

    override fun findById(id: String): App? = repository.findById(id).orElse(null)

    override fun findAll(): List<App> = repository.findAll()

    override fun findAllByOwners(owners: Collection<Owner>): List<App> {
        val userIds = owners.filterIsInstance<Owner.User>().map { it.id }
        val teamIds = owners.filterIsInstance<Owner.Team>().map { it.id }
        return buildList {
            if (userIds.isNotEmpty()) addAll(repository.findAllByOwnerTypeAndOwnerIdIn(AppOwnerType.USER, userIds))
            if (teamIds.isNotEmpty()) addAll(repository.findAllByOwnerTypeAndOwnerIdIn(AppOwnerType.TEAM, teamIds))
        }
    }
}
