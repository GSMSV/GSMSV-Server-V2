-- VM 방화벽을 켰는지. 규칙을 처음 추가할 때 행을 만들고, 규칙을 모두 지워도 행은 남긴다 (켜진 채로 둔다)
CREATE TABLE vm_firewalls
(
    vm_id   VARCHAR(15) PRIMARY KEY REFERENCES vms (id),
    enabled BOOLEAN NOT NULL DEFAULT FALSE
);

-- 목적지 칸은 두지 않는다. 목적지는 언제나 그 VM의 내부 IP로 강제한다
CREATE TABLE firewall_rules
(
    id         VARCHAR(15) PRIMARY KEY,
    vm_id      VARCHAR(15) NOT NULL REFERENCES vms (id),
    action     VARCHAR(8)  NOT NULL CHECK (action IN ('ACCEPT', 'DROP', 'REJECT')),
    direction  VARCHAR(8)  NOT NULL CHECK (direction IN ('IN', 'OUT')),
    protocol   VARCHAR(8)  NOT NULL CHECK (protocol IN ('TCP', 'UDP', 'ICMP')),
    dport      VARCHAR(11),
    source     VARCHAR(49),
    created_at TIMESTAMPTZ NOT NULL
);

-- VM별 목록(만든 순서)과 개수 확인용
CREATE INDEX idx_firewall_rules_vm ON firewall_rules (vm_id, created_at, id);
