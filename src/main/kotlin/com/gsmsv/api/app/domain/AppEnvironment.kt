package com.gsmsv.api.app.domain

import com.gsmsv.api.global.authorization.SubResource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "app_environments")
class AppEnvironment(
    @Id
    @Column(length = 12, nullable = false)
    override val id: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", nullable = false, updatable = false)
    val app: App,
    @Column(nullable = false, length = 30)
    val name: String,
) : SubResource {
    override val parentId: String
        get() = app.id
}
