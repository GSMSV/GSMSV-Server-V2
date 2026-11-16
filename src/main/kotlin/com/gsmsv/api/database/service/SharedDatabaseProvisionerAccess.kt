package com.gsmsv.api.database.service

import com.gsmsv.api.database.provisioner.SharedDatabaseProvisioner
import org.springframework.beans.factory.ObjectProvider

/** 공유 DB 서버 연결은 설정에 따라 빈이 없을 수 있어, 없으면 기동이 아니라 쓰는 시점에 실패한다 */
internal fun ObjectProvider<SharedDatabaseProvisioner>.requireAvailable(): SharedDatabaseProvisioner =
    checkNotNull(ifAvailable) { "공유 DB 서버 연결이 설정되지 않았습니다." }
