-- 자원 FK는 두지 않는다. 자원이 지워져도 알림에는 GRN·이름이 값으로 남아야 한다
CREATE TABLE notifications
(
    id            VARCHAR(12) PRIMARY KEY,
    user_id       VARCHAR(17) NOT NULL REFERENCES users (id),
    kind          VARCHAR(16) NOT NULL,
    category      VARCHAR(32) NOT NULL,
    title         TEXT        NOT NULL,
    body          TEXT        NOT NULL,
    resource_grn  TEXT,
    resource_name TEXT,
    is_read       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL,
    CHECK ((resource_grn IS NULL) = (resource_name IS NULL))
);

-- 사용자별 최근 순 목록과 50건 상한 정리용
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);

CREATE TABLE notification_settings
(
    user_id    VARCHAR(17) NOT NULL REFERENCES users (id),
    group_name VARCHAR(16) NOT NULL,
    email      BOOLEAN     NOT NULL,
    discord    BOOLEAN     NOT NULL,
    PRIMARY KEY (user_id, group_name)
);
