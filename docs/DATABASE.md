# Banco de dados

PostgreSQL 16, esquema só por Flyway (`backend/src/main/resources/db/migration`). Dinheiro em reais (`numeric(10,2)`); datas `timestamptz`.

Tabelas (V1): roles, permissions, role_permissions, users, user_roles, authors, publishers, categories, books,
book_authors, book_categories, book_copies, loans, loan_renewals, reservations, fine_rates, fines, payments, notifications, audit_logs.

Pontos de atenção:
- `book_copies.kind`: `CONSULTA` | `CIRCULANTE`. `loans` referencia `(copy_id, copy_kind)` com `copy_kind = 'CIRCULANTE'`.
- `loans`: índice único parcial garante 1 empréstimo ativo por exemplar.
- `users.password_hash` fica nulo até a Fase 3. Exclusão lógica por `deleted_at` em `users` e `books`.
- Perfil `VISITOR` foi acrescentado aos perfis do pedido original, pois o domínio tem visitante.
- Não há ainda entidades JPA; o esquema é validado só pelo `EsquemaPostgresTest`.

## Migrações
- V1 esquema inicial · V2 `loan_limits` (limite de empréstimos por perfil, padrão 3) · V3 multas/pagamentos em reais (`numeric(10,2)`: `fine_rates.daily_rate`, `fines.amount`, `payments.amount`).
- V4 prazo e renovação em `loan_limits` (`loan_days`, `max_renewals`, `renewal_days`; padrões 14, 2, 7).
- V5 `reservations.copy_id` (exemplar guardado) + índice único, `loan_limits.reservation_pickup_days` (padrão 3) e multa de funcionário em `fine_rates` (R$ 1,00/dia, assumido).
- V6 `notifications.attempts`, `last_error` e `dedup_key` (UNIQUE) para controle de entrega e aviso único.
- V7 índice parcial de falhas de login (bloqueio por força bruta). V8 índices de desempenho (períodos, ativos por vencimento, renovações, reservas por leitor). V9 sessão e senha temporária (`users.token_version`, `must_change_password`), busca sem acento (`pg_trgm`, `unaccent`, função `f_unaccent`, índices GIN em título, autor, nome, e-mail e matrícula) e índices de ordenação e de chaves estrangeiras.
