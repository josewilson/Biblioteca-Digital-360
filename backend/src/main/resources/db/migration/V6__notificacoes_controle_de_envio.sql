-- Controle de envio: tentativas e último erro; chave de deduplicação para avisos que devem sair uma única vez
-- (ex.: "LOAN_DUE_SOON:123"). UNIQUE permite várias linhas com dedup_key nula.
ALTER TABLE notifications ADD COLUMN attempts   integer NOT NULL DEFAULT 0;
ALTER TABLE notifications ADD COLUMN last_error varchar(300);
ALTER TABLE notifications ADD COLUMN dedup_key  varchar(80) UNIQUE;
CREATE INDEX idx_notifications_pending ON notifications(id) WHERE sent_at IS NULL;
