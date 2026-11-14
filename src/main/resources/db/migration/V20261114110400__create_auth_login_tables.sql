-- 로그인 흐름의 1회용 값. 원본 값은 저장하지 않고 SHA-256 해시(hex)만 둔다.
-- 소모는 DELETE ... RETURNING 한 문장으로 해서 같은 값을 두 번 쓸 수 없게 한다.

-- 1.1이 만든 state (5분). 1.2에서 소모한다
CREATE TABLE auth_login_states
(
    state_hash VARCHAR(64) PRIMARY KEY,
    redirect   TEXT        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_login_states_expires_at ON auth_login_states (expires_at);

-- 1.2가 만든 임시 코드 (60초). 1.3에서 소모한다. 1.1의 redirect를 1.3 응답으로 돌려주려고 함께 둔다
CREATE TABLE auth_login_codes
(
    code_hash  VARCHAR(64) PRIMARY KEY,
    user_id    VARCHAR(17) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    redirect   TEXT        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_login_codes_expires_at ON auth_login_codes (expires_at);
