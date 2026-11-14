ALTER TABLE auth_login_states
    ADD COLUMN code_verifier TEXT NOT NULL DEFAULT '';
