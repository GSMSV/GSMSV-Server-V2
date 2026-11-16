CREATE TABLE vms
(
    id                         VARCHAR(15) PRIMARY KEY,
    name                       TEXT        NOT NULL,
    purpose                    TEXT,
    owner_type                 VARCHAR(8)  NOT NULL CHECK (owner_type IN ('USER', 'TEAM')),
    owner_id                   VARCHAR(17) NOT NULL,
    image                      VARCHAR(64) NOT NULL,
    vcpu                       INTEGER     NOT NULL CHECK (vcpu > 0),
    ram_gb                     INTEGER     NOT NULL CHECK (ram_gb > 0),
    ssd_gb                     INTEGER     NOT NULL CHECK (ssd_gb > 0),
    internal_ip                VARCHAR(45),
    status                     VARCHAR(16) NOT NULL CHECK (status IN ('CREATING', 'ACTIVE', 'SUSPENDED', 'DELETING', 'DELETED', 'FAILED')),
    initialized                BOOLEAN     NOT NULL DEFAULT FALSE,
    initial_username           VARCHAR(32) NOT NULL,
    initial_password_encrypted TEXT        NOT NULL,
    created_by                 VARCHAR(17) NOT NULL REFERENCES users (id),
    created_at                 TIMESTAMPTZ NOT NULL,
    updated_at                 TIMESTAMPTZ NOT NULL,
    expires_at                 TIMESTAMPTZ NOT NULL,
    deleted_at                 TIMESTAMPTZ
);

-- 소유자는 개인·팀 두 곳을 가리키므로 외래 키를 걸 수 없다. 소유자별 목록·사용량 합계 조회용 인덱스만 둔다
CREATE INDEX idx_vms_owner ON vms (owner_type, owner_id) WHERE deleted_at IS NULL;

-- 삭제 표시된 행은 정리 작업에서만 조회하므로 삭제된 행만 담는다
CREATE INDEX idx_vms_deleted_at ON vms (deleted_at) WHERE deleted_at IS NOT NULL;
