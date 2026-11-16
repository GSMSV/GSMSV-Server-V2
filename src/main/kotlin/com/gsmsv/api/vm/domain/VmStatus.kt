package com.gsmsv.api.vm.domain

/**
 * VM 저장 상태. 전원·가동 같은 실시간 값은 여기에 두지 않는다 (캐시로 덧붙인다).
 *
 * **(미정)** 만료·정지 상태 값은 자원 수명 공통 처리(#32)와 맞춘다. 지금은 DB 상태와 같은 모양이다.
 * 바꾸는 방법은 [Vm]의 메서드뿐이다.
 */
enum class VmStatus {
    CREATING,
    ACTIVE,

    /** 만료로 멈춘 상태. 연장하면 ACTIVE로 돌아간다 */
    SUSPENDED,
    DELETING,

    /** 삭제 표시만 남은 상태 */
    DELETED,
    FAILED,
}
