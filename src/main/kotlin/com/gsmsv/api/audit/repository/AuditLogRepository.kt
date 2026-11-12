package com.gsmsv.api.audit.repository

import com.gsmsv.api.audit.domain.AuditLog
import org.springframework.data.jpa.repository.JpaRepository

interface AuditLogRepository : JpaRepository<AuditLog, Long>
