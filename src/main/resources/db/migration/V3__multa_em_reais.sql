-- Valores de multa e pagamento passam de centavos inteiros para reais com 2 casas (numeric(10,2), sem erro de ponto flutuante).
ALTER TABLE fine_rates RENAME COLUMN cents_per_day TO daily_rate;
ALTER TABLE fine_rates ALTER COLUMN daily_rate TYPE numeric(10,2) USING daily_rate / 100.0;

ALTER TABLE fines RENAME COLUMN amount_cents TO amount;
ALTER TABLE fines ALTER COLUMN amount TYPE numeric(10,2) USING amount / 100.0;

ALTER TABLE payments RENAME COLUMN amount_cents TO amount;
ALTER TABLE payments ALTER COLUMN amount TYPE numeric(10,2) USING amount / 100.0;
