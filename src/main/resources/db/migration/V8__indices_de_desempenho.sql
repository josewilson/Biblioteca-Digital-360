-- Índices achados na revisão de desempenho (medida com 205 mil empréstimos, 150 mil exemplares, 500 mil linhas de auditoria).
-- O PostgreSQL não cria índice para chave estrangeira sozinho; estes cobrem as consultas reais do sistema.

-- Painel (movimento diário) e relatórios por período: filtro por intervalo de datas.
CREATE INDEX idx_loans_loaned_at   ON loans (loaned_at);
CREATE INDEX idx_loans_returned_at ON loans (returned_at) WHERE returned_at IS NOT NULL;
CREATE INDEX idx_fines_created_at  ON fines (created_at);
CREATE INDEX idx_payments_paid_at  ON payments (paid_at);

-- Empréstimos em andamento e atrasados (balcão, painel, relatório de atrasos, aviso de vencimento).
CREATE INDEX idx_loans_active_due ON loans (due_at) WHERE returned_at IS NULL;

-- Chaves estrangeiras consultadas: contagem de renovações por empréstimo e reservas de um leitor.
CREATE INDEX idx_loan_renewals_loan ON loan_renewals (loan_id);
CREATE INDEX idx_reservations_user  ON reservations (user_id);
