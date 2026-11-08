-- Reserva guarda o exemplar separado para o 1º da fila; prazo de retirada é configurável por perfil.
ALTER TABLE reservations ADD COLUMN copy_id bigint REFERENCES book_copies(id);
-- Um exemplar só pode estar guardado para uma reserva por vez.
CREATE UNIQUE INDEX uq_reservation_copy_hold ON reservations(copy_id) WHERE status = 'DISPONIVEL';
ALTER TABLE reservations ADD CONSTRAINT ck_reservation_copy_when_available
    CHECK ((status = 'DISPONIVEL') = (copy_id IS NOT NULL AND expires_at IS NOT NULL) OR status <> 'DISPONIVEL');

-- Padrão ASSUMIDO: 3 dias para retirar o exemplar reservado (ajustável em loan_limits).
ALTER TABLE loan_limits ADD COLUMN reservation_pickup_days integer NOT NULL DEFAULT 3 CHECK (reservation_pickup_days >= 1);

-- Funcionário também paga multa. Valor ASSUMIDO: R$ 1,00 por dia (igual ao aluno); a administração deve ajustar.
INSERT INTO fine_rates (role_id, daily_rate)
SELECT id, 1.00 FROM roles WHERE code = 'EMPLOYEE';
