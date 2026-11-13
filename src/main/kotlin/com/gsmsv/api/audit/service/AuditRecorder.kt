package com.gsmsv.api.audit.service

import com.gsmsv.api.audit.domain.AuditEventType
import com.gsmsv.api.global.authorization.Actor

/**
 * 다른 도메인이 감사 로그를 남길 때 쓰는 공개 인터페이스.
 *
 * 호출한 쪽의 트랜잭션 안에서 저장한다. 기록이 실패하면 동작도 함께 실패한다.
 * 비밀번호를 보여 주는 동작이 기록 없이 끝나면 안 되기 때문이다. 그래서 응답을 만들기 전에 부른다.
 */
interface AuditRecorder {
    /**
     * @param target 동작 대상. 서비스 자원 GRN, 하위 자원 표기, 사용자·팀 id 중 하나다
     * @param details 부가 정보(예: 되살릴 때의 원래 소유자). 비밀값·내부 IP는 넣지 않는다
     * @throws IllegalArgumentException [details]에 비밀값이나 IP 주소로 보이는 것이 있으면
     */
    fun record(
        actor: Actor,
        eventType: AuditEventType,
        target: String,
        details: Map<String, String> = emptyMap(),
    )
}
