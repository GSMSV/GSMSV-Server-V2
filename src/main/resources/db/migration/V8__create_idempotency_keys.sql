CREATE TABLE idempotency_keys
(
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(17)  NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    -- 메서드·경로·본문을 합친 SHA-256 (hex). 같은 키로 다른 요청이 왔는지 가린다
    fingerprint     VARCHAR(64)  NOT NULL,
    -- 처리 중에는 NULL. 응답이 나오면 채운다
    response_status INTEGER,
    content_type    TEXT,
    response_body   BYTEA,
    created_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_idempotency_keys_user_key UNIQUE (user_id, idempotency_key)
);

-- 만료된 키를 정리하는 작업용
CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);
