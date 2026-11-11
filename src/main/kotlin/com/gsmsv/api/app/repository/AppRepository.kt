package com.gsmsv.api.app.repository

import com.gsmsv.api.app.domain.App
import com.gsmsv.api.app.domain.AppOwnerType
import org.springframework.data.jpa.repository.JpaRepository

interface AppRepository : JpaRepository<App, String> {
    fun findAllByOwnerTypeAndOwnerIdIn(
        ownerType: AppOwnerType,
        ownerIds: Collection<String>,
    ): List<App>
}
