package com.gsmsv.api.database.domain

/** 상태 이름은 API 명세와 함께 확정한다. 바꾸는 방법은 [Database]의 메서드뿐이다. */
enum class DatabaseStatus {
    CREATING,
    ACTIVE,

    /** 만료로 계정을 잠근 상태. 데이터는 남기고, 연장하면 ACTIVE로 돌아간다 */
    SUSPENDED,
    DELETING,

    /** 삭제 표시만 남은 상태. 7일 안에 관리자가 원래 GRN으로 되살릴 수 있다 */
    DELETED,
    FAILED,
}
