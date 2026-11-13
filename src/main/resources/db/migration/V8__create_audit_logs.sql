-- 자원·사용자 FK는 두지 않는다. 자원이 지워지고 사용자가 탈퇴해도 감사 기록은 값으로 남아야 한다
-- 비밀번호·토큰 같은 비밀값과 내부 IP는 어느 칸에도 저장하지 않는다
CREATE TABLE audit_logs
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id VARCHAR(70),
    actor_type VARCHAR(16) NOT NULL CHECK (actor_type IN ('USER', 'ADMIN', 'SYSTEM')),
    actor_id   VARCHAR(17),
    event_type VARCHAR(48) NOT NULL,
    -- 서비스 자원 GRN, 하위 자원 표기, 사용자·팀 id를 모두 받는다
    target     TEXT        NOT NULL,
    details    TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK ((actor_type = 'SYSTEM') = (actor_id IS NULL))
);

-- 최근 순 커서 목록용
CREATE INDEX idx_audit_logs_created ON audit_logs (created_at DESC, id DESC);

-- 요청 ID로 원인을 찾을 때 쓴다
CREATE INDEX idx_audit_logs_request ON audit_logs (request_id);
