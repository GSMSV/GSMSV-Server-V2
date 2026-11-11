CREATE TABLE database_backups
(
    id          VARCHAR(15) PRIMARY KEY,
    database_id VARCHAR(15) NOT NULL REFERENCES databases (id),
    kind        VARCHAR(16) NOT NULL CHECK (kind IN ('DAILY', 'PRE_RESTORE')),
    size_bytes  BIGINT      NOT NULL CHECK (size_bytes >= 0),
    storage_key TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL
);

-- DB별 백업 목록과 하루 1회 백업 여부 확인용
CREATE INDEX idx_database_backups_database ON database_backups (database_id, created_at DESC);

-- 만료된 백업을 찾는 정리 작업용
CREATE INDEX idx_database_backups_expires_at ON database_backups (expires_at);
