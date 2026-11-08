-- Prazo e renovação por perfil, junto do limite de empréstimos (configuráveis pela administração).
-- Os valores abaixo são PADRÕES INICIAIS assumidos (14 dias, 2 renovações de 7 dias); ajuste conforme a instituição.
ALTER TABLE loan_limits ADD COLUMN loan_days     integer NOT NULL DEFAULT 14 CHECK (loan_days >= 1);
ALTER TABLE loan_limits ADD COLUMN max_renewals  integer NOT NULL DEFAULT 2  CHECK (max_renewals >= 0);
ALTER TABLE loan_limits ADD COLUMN renewal_days  integer NOT NULL DEFAULT 7  CHECK (renewal_days >= 1);
