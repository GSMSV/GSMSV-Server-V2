package com.gsmsv.api.database.service

import com.gsmsv.api.database.domain.DatabaseBackup
import com.gsmsv.api.database.repository.DatabaseBackupRepository
import com.gsmsv.api.global.authorization.SubResourceReader
import com.gsmsv.api.global.grn.SubResourceType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 백업 하위 자원 조회 포트. 부모 DB가 요청과 같은지는 `SubResourceLoader`가 확인한다. */
@Component
class DatabaseBackupResourceReader(
    private val backupRepository: DatabaseBackupRepository,
) : SubResourceReader<DatabaseBackup> {
    override val type: SubResourceType = SubResourceType.BACKUP

    @Transactional(readOnly = true)
    override fun findById(id: String): DatabaseBackup? = backupRepository.findById(id).orElse(null)
}
