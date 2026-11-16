CREATE TABLE teams
(
    id               VARCHAR(17) PRIMARY KEY,
    name             TEXT        NOT NULL,
    status           VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    rejection_reason TEXT,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL
);

CREATE TABLE team_members
(
    id         VARCHAR(16) PRIMARY KEY,
    team_id    VARCHAR(17) NOT NULL REFERENCES teams (id),
    user_id    VARCHAR(17) NOT NULL REFERENCES users (id),
    role       VARCHAR(16) NOT NULL CHECK (role IN ('LEADER', 'DEVOPS', 'MEMBER')),
    UNIQUE (team_id, user_id)
);

CREATE UNIQUE INDEX uq_team_members_leader ON team_members (team_id) WHERE role = 'LEADER';
CREATE INDEX idx_team_members_user_id ON team_members (user_id);
