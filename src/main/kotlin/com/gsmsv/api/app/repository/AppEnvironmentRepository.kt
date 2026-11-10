package com.gsmsv.api.app.repository

import com.gsmsv.api.app.domain.AppEnvironment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AppEnvironmentRepository : JpaRepository<AppEnvironment, String> {
    @Query("select environment from AppEnvironment environment where environment.app.id = :appId")
    fun findAllByAppId(
        @Param("appId") appId: String,
    ): List<AppEnvironment>

    @Query("select count(environment) from AppEnvironment environment where environment.app.id = :appId")
    fun countByAppId(
        @Param("appId") appId: String,
    ): Long
}
