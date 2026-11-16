CREATE TABLE apps
(
    id         VARCHAR(16) PRIMARY KEY,
    name       VARCHAR(30) NOT NULL,
    owner_type VARCHAR(4)  NOT NULL CHECK (owner_type IN ('USER', 'TEAM')),
    owner_id   VARCHAR(17) NOT NULL,
    created_by VARCHAR(17) NOT NULL,
    status     TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_apps_owner ON apps (owner_type, owner_id);

CREATE TABLE app_environments
(
    id     VARCHAR(12) PRIMARY KEY,
    app_id VARCHAR(16) NOT NULL REFERENCES apps (id),
    name   VARCHAR(30) NOT NULL,
    UNIQUE (app_id, name)
);

CREATE INDEX idx_app_environments_app_id ON app_environments (app_id);
