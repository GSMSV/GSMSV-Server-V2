-- 오래 걸리는 작업(operation)의 상태. 진실은 이 테이블이고, 작업을 하는 쪽(공유 DB 서버·Infra)의 기록은 참고만 한다.
-- 대상은 서비스 자원 하나를 종류와 id로 가리킨다. 자원마다 테이블이 달라 외래 키를 걸 수 없다
CREATE TABLE operations
(
    id            VARCHAR(15) PRIMARY KEY,
    type          VARCHAR(32) NOT NULL,
    status        VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED')),
    stage         VARCHAR(16),
    resource_type VARCHAR(8)  NOT NULL,
    resource_id   VARCHAR(15) NOT NULL,
    requested_by  VARCHAR(17) NOT NULL REFERENCES users (id),
    error_code    VARCHAR(64),
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    finished_at   TIMESTAMPTZ
);

CREATE INDEX idx_operations_resource ON operations (resource_type, resource_id);
