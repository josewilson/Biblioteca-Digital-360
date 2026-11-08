# Testes

Contagens da última execução completa: **backend 119** (`mvnw test`) e **frontend 44** (`npm test`), todos passando.

## Backend (`./mvnw test`, Windows: `mvnw.cmd test`)
| Tipo | Onde | O que prova |
|---|---|---|
| Unidade (Mockito, relógio fixo) | `*ServiceTest`, `ReportCsvTest`, `SmtpChannelTest`, `JwtServiceTest` | regras de empréstimo, multa, reserva, pagamento, isenção; CSV seguro; JWT |
| Segurança de endpoints (`@WebMvcTest`) | `*SecurityTest` | quem recebe 401/403/400, inclusive que corpo inválido não vaza mensagem a quem não tem permissão |
| Fluxo contra PostgreSQL real (Testcontainers) | `*FluxoTest`, `EsquemaPostgresTest` | migrações V1–V6, mapeamento JPA, regras impostas pelo banco, painel e relatórios com números exatos, avisos |
| Concorrência | `EmprestimoFluxoTest`, `ReservaMultaFluxoTest` | 5 balcões no mesmo exemplar = 1 empréstimo; 6 empréstimos do mesmo leitor = 3; 4 pagamentos da mesma multa = 1 |

Os testes de fluxo **precisam do Docker** e são pulados sem ele (o Maven mostra `Skipped`). Um relógio controlável (`MutableClock`) simula atrasos sem esperar. O agendamento é desligado nos testes (`src/test/resources/application.properties`); as rotinas são chamadas à mão.

## Frontend (`cd frontend`, `npm test`)
Vitest + Testing Library em jsdom, com `fetch` simulado (`src/test/utils.tsx`).
- `lib/api.test.ts`: token e query string, `ProblemDetail` vira mensagem, queda de sessão no 401, servidor fora do ar, 204.
- `lib/auth.test.ts`: leitura do JWT, token vencido ou malformado.
- `schemas.test.ts`: regra "1 consulta + 1 emprestável", códigos repetidos, CPF, senha, justificativa.
- `access.test.tsx`: login obrigatório, token vencido descartado, menu e telas por perfil, "Acesso negado", sair, 401 derruba a sessão.
- `login.test.tsx`: validação, erro anunciado ao leitor de tela, sucesso, credencial errada.
- `pages.test.tsx`: painel, formulário de obra, balcão (empréstimo, regra recusada, devolução com multa), multas (pagamento, isenção só para quem pode), reserva e baixa de exemplar.

`npm run typecheck` (TypeScript estrito) e `npm run build` fazem parte da verificação.

## Verificado à mão, uma vez (não é teste automatizado)
Backend real + PostgreSQL descartável + Vite: login, cadastro de obra, empréstimo, devolução, painel, relatório e CSV **pelo proxy do Vite**, conferindo os campos de cada resposta com `frontend/src/lib/types.ts`. Tudo conferiu.

## O que NÃO está coberto
- **Aparência e uso real no navegador:** nenhum teste abre um navegador. Layout responsivo, gráficos desenhados e foco por teclado **não foram vistos**. (Playwright, Fase 7.)
- **Acessibilidade por ferramenta** (axe/Lighthouse): não rodou. Só as práticas listadas no AGENTS.md foram seguidas.
- **E-mail SMTP real:** só testado com remetente simulado.
- **Desempenho e carga:** nenhum teste.
