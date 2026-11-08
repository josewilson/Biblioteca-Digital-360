-- Apoia o bloqueio por força bruta: contar falhas recentes de um e-mail sem varrer a auditoria inteira.
CREATE INDEX idx_audit_login_failed ON audit_logs (details, created_at) WHERE action = 'LOGIN_FAILED';
