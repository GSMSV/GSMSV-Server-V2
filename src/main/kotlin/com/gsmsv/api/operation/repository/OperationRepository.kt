package com.gsmsv.api.operation.repository

import com.gsmsv.api.operation.domain.Operation
import org.springframework.data.jpa.repository.JpaRepository

interface OperationRepository : JpaRepository<Operation, String>
