CREATE TABLE https_domains
(
    id            VARCHAR(15) PRIMARY KEY,
    vm_id         VARCHAR(15) NOT NULL REFERENCES vms (id),
    subdomain     VARCHAR(63) NOT NULL,
    internal_port INTEGER     NOT NULL CHECK (internal_port BETWEEN 1 AND 65535),
    sync_status   VARCHAR(8)  NOT NULL CHECK (sync_status IN ('PENDING', 'SYNCED', 'FAILED')),
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);

-- 서브도메인은 전체 서비스에서 하나만 쓸 수 있다. 같은 이름을 동시에 잡으려 해도 이 제약이 하나만 통과시킨다
CREATE UNIQUE INDEX uq_https_domains_subdomain ON https_domains (subdomain);

-- VM별 도메인 목록과 개수 검사용
CREATE INDEX idx_https_domains_vm ON https_domains (vm_id, created_at);
