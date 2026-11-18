CREATE TABLE operations
(
    id              VARCHAR(15) PRIMARY KEY,
    type            VARCHAR(24) NOT NULL,
    target_grn      VARCHAR(80) NOT NULL,
    owner_type      VARCHAR(8)  NOT NULL CHECK (owner_type IN ('USER', 'TEAM')),
    owner_id        VARCHAR(17) NOT NULL,
    created_by      VARCHAR(17) NOT NULL,
    status          VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED')),
    stage           VARCHAR(16) NOT NULL CHECK (stage IN ('QUEUED', 'CLONING', 'CONFIGURING', 'BOOTING', 'NETWORKING', 'RUNNING')),
    queue_position  INTEGER     NOT NULL DEFAULT 0 CHECK (queue_position >= 0),
    job_id          VARCHAR(64),
    error_code      VARCHAR(64),
    error_message   TEXT,
    error_retryable BOOLEAN,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ,
    -- 실패한 작업에만 원인이 있고, 끝난 작업에만 끝난 시각이 있다 (계약 Operation)
    CONSTRAINT ck_operations_error CHECK ((status = 'FAILED') = (error_code IS NOT NULL)),
    CONSTRAINT ck_operations_finished CHECK ((status IN ('SUCCEEDED', 'FAILED', 'CANCELED')) = (finished_at IS NOT NULL))
);

-- 대상 자원이 지워진 뒤에도 보관 기간 동안 조회하므로 대상 자원·사용자에 외래 키를 걸지 않는다

-- 한 Infra 작업은 operation 하나에만 붙는다
CREATE UNIQUE INDEX uq_operations_job ON operations (job_id) WHERE job_id IS NOT NULL;

-- 진행 중인 작업 폴링용
CREATE INDEX idx_operations_active ON operations (created_at) WHERE finished_at IS NULL;

-- 보관 기간이 지난 기록 정리용
CREATE INDEX idx_operations_finished ON operations (finished_at) WHERE finished_at IS NOT NULL;
