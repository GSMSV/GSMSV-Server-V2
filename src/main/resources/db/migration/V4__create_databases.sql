CREATE TABLE databases
(
    id              VARCHAR(15) PRIMARY KEY,
    name            TEXT        NOT NULL,
    owner_type      VARCHAR(8)  NOT NULL CHECK (owner_type IN ('USER', 'TEAM')),
    owner_id        VARCHAR(17) NOT NULL,
    engine          VARCHAR(16) NOT NULL CHECK (engine IN ('MYSQL', 'POSTGRESQL')),
    isolation       VARCHAR(16) NOT NULL CHECK (isolation IN ('SHARED', 'DEDICATED')),
    status          VARCHAR(16) NOT NULL CHECK (status IN ('CREATING', 'ACTIVE', 'SUSPENDED', 'DELETING', 'DELETED', 'FAILED')),
    write_locked    BOOLEAN     NOT NULL DEFAULT FALSE,
    max_connections INTEGER     NOT NULL CHECK (max_connections > 0),
    created_by      VARCHAR(17) NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ
);

-- 소유자는 개인·팀 두 곳을 가리키므로 외래 키를 걸 수 없다. 소유자별 목록 조회용 인덱스만 둔다
CREATE INDEX idx_databases_owner ON databases (owner_type, owner_id) WHERE deleted_at IS NULL;
