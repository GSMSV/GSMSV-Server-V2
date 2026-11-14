CREATE TABLE port_forwards
(
    id             VARCHAR(13) PRIMARY KEY,
    vm_id          VARCHAR(15) NOT NULL REFERENCES vms (id),
    kind           VARCHAR(8)  NOT NULL CHECK (kind IN ('SSH', 'HTTP', 'SVC', 'CUSTOM')),
    external_port  INTEGER     NOT NULL CHECK (external_port BETWEEN 1 AND 65535),
    internal_port  INTEGER     NOT NULL CHECK (internal_port BETWEEN 1 AND 65535),
    protocol       VARCHAR(3)  NOT NULL CHECK (protocol IN ('TCP', 'UDP')),
    allowed_source VARCHAR(43),
    description    TEXT,
    created_at     TIMESTAMPTZ NOT NULL
);

-- 외부 포트는 모든 VM이 함께 쓰는 한 공간이다. 두 요청이 같은 번호를 고르면 나중 것이 이 제약에 걸려 선점에 실패한다
CREATE UNIQUE INDEX uq_port_forwards_external_port ON port_forwards (external_port);

-- VM별 포트 목록과 커스텀 개수 검사용
CREATE INDEX idx_port_forwards_vm ON port_forwards (vm_id, kind);
