package com.gsmsv.api.database.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * DB 백업 설정 (24번 DBaaS 3.7).
 *
 * [directory]가 비어 있으면 백업 기능(저장소·서비스·스케줄러)을 만들지 않는다. 저장 위치는 환경 변수로 받는다.
 */
@ConfigurationProperties("gsmsv.database.backup")
data class DatabaseBackupProperties(
    val directory: String = "",
    /** 백업 보관 일수. 삭제된 DB의 백업도 삭제 뒤 이 기간 동안 남긴다 */
    val retentionDays: Int = 7,
    /** 일일 백업 시각 (UTC, cron) */
    val cron: String = "0 0 18 * * *",
)
