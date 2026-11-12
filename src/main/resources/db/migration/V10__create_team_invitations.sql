CREATE TABLE team_invitations
(
    id          VARCHAR(16) PRIMARY KEY,
    team_id     VARCHAR(17)  NOT NULL REFERENCES teams (id),
    email       TEXT         NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    sent_at     TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    canceled_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ
);

CREATE INDEX idx_team_invitations_active_page ON team_invitations (team_id, sent_at DESC, id DESC);
CREATE UNIQUE INDEX uq_team_invitations_active_email ON team_invitations (team_id, email)
    WHERE canceled_at IS NULL AND accepted_at IS NULL;
