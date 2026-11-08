# Desempenho

Revisão da Fase 7: **medida**, não estimada. Cada número abaixo veio de execução real.

## Como foi medido
- PostgreSQL 16 em Docker + backend real (`spring-boot:run`), na mesma máquina de desenvolvimento (Windows, Docker Desktop).
- Carga: **50 mil obras, 150 mil exemplares, 20 mil leitores, 205 mil empréstimos** (5 mil em andamento, metade atrasada), 20 mil multas, 3 mil reservas, **500 mil registros de auditoria**, 100 mil notificações.
- Cada endpoint: 1 chamada de aquecimento + 3 medidas via `curl`; vale a **mediana**. A quantidade de consultas vem do log de comandos do PostgreSQL (vê tudo, JPA e SQL direto).
- Variação entre execuções seguidas: **± 20–30%**. Diferenças menores que isso são ruído (ver "limites da medição").

## Resultado

| Endpoint | Antes | Depois | Consultas SQL antes → depois |
|---|---|---|---|
| `GET /dashboard` | **5 503 ms** | **302 ms** | 5 → 4 |
| `GET /users?size=200` (seletor de leitor) | 453 ms | 61 ms | **203 → 5** |
| `GET /loans` (todos) | 129 ms | 42 ms | 23 → 3 |
| `GET /loans?activeOnly` (balcão) | 47 ms | 23 ms | 23 → 3 |
| `GET /reservations` | 60 ms | 26 ms | 23 → 4 |
| `GET /fines` | 37 ms | 32 ms | 13 → 3 |
| `GET /books` / busca por título | 73 / 107 ms | 75 / 83 ms | 14 → 6 |
| Relatórios (loans, returns, overdue, fines, top-books, availability, users) | 26–243 ms | 31–270 ms | 2 → 2 (sem mudança) |
| `POST /auth/login` (senha errada) | 189 ms | 190 ms | — (custo do BCrypt, intencional) |

## O que estava errado e o que foi feito
1. **Painel, 5,5 s.** A "movimentação dos últimos 30 dias" fazia 2 subconsultas por dia (60 no total), cada uma varrendo **todos** os empréstimos com uma expressão sem índice (~80 ms cada). Agora é **uma** passagem com filtro por intervalo (usa `idx_loans_loaned_at`/`idx_loans_returned_at`), e os dois rankings (obras e categorias) compartilham a mesma agregação. → 302 ms.
2. **N+1 nas listas.** Cada linha disparava consultas extras (exemplar, obra, editora, empréstimo, perfis, posição na fila). Corrigido com `@EntityGraph` (empréstimos, multas, reservas, obras), `@BatchSize` (perfis dos usuários) e uma consulta única para as posições da fila.
3. **Índices que faltavam** (migração V8): datas de período (`loaned_at`, `returned_at`, `fines.created_at`, `payments.paid_at`), empréstimos ativos por vencimento, renovações por empréstimo, reservas por leitor. Para a contagem de falhas de login, o índice parcial da V7.

Os relatórios já eram aceitáveis (≤ 270 ms com 205 mil empréstimos) e não foram alterados.

## Regressão guardada por teste
`ConsultasPorRequisicaoTest` (PostgreSQL real, 12 linhas por lista) conta as consultas JPA de cada lista com as estatísticas do Hibernate e **falha se alguma voltar a custar uma consulta por linha**. Valores atuais: empréstimos 1, usuários 2, reservas 2, obras 4, multas 1.

## Limites conhecidos (não resolvidos de propósito)
- **O painel cresce com o histórico:** os rankings agregam todos os empréstimos de todos os tempos (~1,2 ms por mil empréstimos). Em ~1 milhão de empréstimos passaria de ~1,5 s; aí o caminho é guardar contadores ou cachear alguns segundos. Hoje não se justifica.
- **Busca por título** usa `LIKE '%texto%'` (varredura de 50 mil obras: ~80 ms). Um índice `pg_trgm` aceleraria, mas exige extensão no banco; só vale com centenas de milhares de obras.
- **Relatório `availability`** agrega todas as obras (limitado a 10 mil linhas): ~260 ms com 50 mil obras.
- **Frontend:** o código dos gráficos (~114 kB comprimidos) é carregado só no painel; o restante do aplicativo tem ~167 kB comprimidos. Não foi medido tempo de carregamento em navegador (Lighthouse não rodou).

## Limites da medição
- Uma só máquina, um só usuário por vez: **não é teste de carga**. Concorrência foi verificada só quanto à *correção* (travas), não quanto a vazão.
- Números absolutos mudam com o hardware; vale a *proporção* antes/depois e a contagem de consultas, que é determinística.
- O relatório de obras mais emprestadas pareceu piorar (218 → 270 ms), mas as consultas SQL dele não mudaram: o `EXPLAIN` mostra 127 ms com o índice novo contra 142 ms sem ele. É ruído da medição.
