package com.gsmsv.api.example.repository

import com.gsmsv.api.example.domain.Example
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.ServiceResourceReader
import com.gsmsv.api.global.grn.ResourceType

/**
 * 예시 자원 조회 포트의 메모리 구현. 실제 도메인에서는 JPA repository를 감싸 [ServiceResourceReader]를 구현한다.
 *
 * 이 reader는 로더(`ServiceResourceLoader`)와 `listVisible` 밖에서 부르지 않는다.
 */
class InMemoryExampleReader(
    examples: List<Example>,
) : ServiceResourceReader<Example> {
    private val examples = examples.associateBy { it.id }

    // 예시라 VM 접두사(`vm-`)를 빌려 쓴다. 실제 도메인은 자기 종류를 둔다
    override val type: ResourceType = ResourceType.VM

    override fun findById(id: String): Example? = examples[id]

    override fun findAll(): List<Example> = examples.values.toList()

    override fun findAllByOwners(owners: Collection<Owner>): List<Example> = examples.values.filter { it.owner in owners }
}
