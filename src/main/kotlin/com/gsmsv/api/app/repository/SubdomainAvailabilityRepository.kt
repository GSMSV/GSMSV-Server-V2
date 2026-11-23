package com.gsmsv.api.app.repository

import org.springframework.stereotype.Repository

interface SubdomainAvailabilityRepository {
    fun existsByName(name: String): Boolean
}

@Repository
class EmptySubdomainAvailabilityRepository : SubdomainAvailabilityRepository {
    // #157에서 칸 저장소가 생기면 production 서브도메인 조회로 교체한다.
    override fun existsByName(name: String): Boolean = false
}
