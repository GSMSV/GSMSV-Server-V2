CREATE TABLE users
(
    id             VARCHAR(17) PRIMARY KEY,
    datagsm_id     TEXT        NOT NULL,
    email          TEXT        NOT NULL,
    name           TEXT        NOT NULL,
    role           VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    status         VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    grade          INTEGER,
    class_number   INTEGER,
    student_number INTEGER,
    created_at     TIMESTAMPTZ NOT NULL,
    deleted_at     TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_users_active_email ON users (email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_users_active_datagsm_id ON users (datagsm_id) WHERE deleted_at IS NULL;
