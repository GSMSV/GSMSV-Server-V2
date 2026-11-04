package com.gsmsv.api.example.domain

import com.gsmsv.api.global.authorization.OwnedResource
import com.gsmsv.api.global.authorization.Owner
import java.time.Instant

/**
 * 예시 서비스 자원. 실제 도메인에서는 JPA 엔티티(`data class` 금지)로 만들고 [OwnedResource]를 구현한다.
 *
 * 소유자는 만든 뒤 바꾸지 않는다 (엔티티에서는 `updatable = false`로 매핑한다).
 * 가짜 저장소라 응답에 쓸 소유자·만든 사람 이름을 자원에 같이 둔다. 실제로는 사용자·팀 service에서 가져온다.
 */
class Example(
    override val id: String,
    override val owner: Owner,
    val name: String,
    val ownerName: String,
    val createdByName: String,
    val createdAt: Instant,
    /** 삭제를 막아 둔 자원. 도메인 규칙으로 오류를 던지는 예시에 쓴다 */
    val locked: Boolean = false,
) : OwnedResource

enum class ExampleStatus {
    RUNNING,
}
