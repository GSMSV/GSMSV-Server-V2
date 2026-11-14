CREATE TABLE auth_refresh_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    user_id VARCHAR(17) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_refresh_tokens_user_id ON auth_refresh_tokens(user_id);
CREATE INDEX idx_auth_refresh_tokens_expires_at ON auth_refresh_tokens(expires_at);
