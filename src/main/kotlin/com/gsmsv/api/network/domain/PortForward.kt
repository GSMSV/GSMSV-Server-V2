package com.gsmsv.api.network.domain

import com.gsmsv.api.global.authorization.SubResource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * VM 1대의 포트포워딩 1개 (포트포워딩 관리 12번). VM의 하위 자원이다.
 *
 * 기본 포트 3종([PortForwardKind.SSH]·[PortForwardKind.HTTP]·[PortForwardKind.SVC])은 VM을 만들 때 [createDefault]로,
 * 커스텀 포트는 사용자 요청으로 [createCustom]으로 만든다. 만든 뒤 바꾸지 않는다. 지울 때는 행을 지운다.
 * 외부 포트는 모든 VM에서 유일하다 (DB 유니크 제약).
 */
@Entity
@Table(name = "port_forwards")
class PortForward(
    @Id
    @Column(length = 13, nullable = false)
    override val id: String,
    @Column(name = "vm_id", nullable = false, updatable = false, length = 15)
    val vmId: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 8)
    val kind: PortForwardKind,
    @Column(name = "external_port", nullable = false, updatable = false)
    val externalPort: Int,
    @Column(name = "internal_port", nullable = false, updatable = false)
    val internalPort: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 3)
    val protocol: PortForwardProtocol,
    /** IP 또는 CIDR. 없으면 모두 허용한다 */
    @Column(name = "allowed_source", updatable = false, length = 43)
    val allowedSource: String?,
    @Column(updatable = false, columnDefinition = "text")
    val description: String?,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) : SubResource {
    override val parentId: String
        get() = vmId

    init {
        require(externalPort in PORT_RANGE) { "외부 포트는 1~65535여야 합니다." }
        require(internalPort in PORT_RANGE) { "내부 포트는 1~65535여야 합니다." }
    }

    companion object {
        val PORT_RANGE = 1..65535

        /** VM 생성 흐름이 부른다. 기본 포트는 모두 허용, TCP다 */
        fun createDefault(
            id: String,
            vmId: String,
            kind: PortForwardKind,
            externalPort: Int,
            internalPort: Int,
        ): PortForward {
            require(kind != PortForwardKind.CUSTOM) { "기본 포트 종류만 만들 수 있습니다." }
            return PortForward(id, vmId, kind, externalPort, internalPort, PortForwardProtocol.TCP, null, null)
        }

        fun createCustom(
            id: String,
            vmId: String,
            externalPort: Int,
            internalPort: Int,
            protocol: PortForwardProtocol,
            allowedSource: String?,
            description: String?,
        ): PortForward = PortForward(id, vmId, PortForwardKind.CUSTOM, externalPort, internalPort, protocol, allowedSource, description)
    }
}

enum class PortForwardKind {
    SSH,
    HTTP,
    SVC,
    CUSTOM,
}

enum class PortForwardProtocol {
    TCP,
    UDP,
}
