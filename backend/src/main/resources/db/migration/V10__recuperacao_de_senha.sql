-- Recuperação de senha por e-mail: só o hash (SHA-256) do token fica no banco; o token em si vai no link do e-mail.
-- Uso único (used_at) e validade curta (expires_at).
CREATE TABLE password_resets (
    id         bigserial PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users(id),
    token_hash char(64)    NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_password_resets_user ON password_resets (user_id, created_at);
