package com.gsmsv.api.database.service

import com.gsmsv.api.global.authorization.Owner
import org.springframework.stereotype.Component

/**
 * DB 사용 용량(GiB) 조회 포트. 응답의 `storage`를 채운다.
 *
 * **(미정)** 용량 측정 작업은 아직 없다. 측정 결과가 저장되기 전까지 [PlaceholderDatabaseUsageReader]가 0을 돌려준다.
 */
interface DatabaseUsageReader {
    fun usedGib(databaseId: String): Double

    /** 소유자가 가진 공유형 DB 전체의 사용 용량 */
    fun ownerUsedGib(owner: Owner): Double
}

@Component
class PlaceholderDatabaseUsageReader : DatabaseUsageReader {
    override fun usedGib(databaseId: String): Double = 0.0

    override fun ownerUsedGib(owner: Owner): Double = 0.0
}
