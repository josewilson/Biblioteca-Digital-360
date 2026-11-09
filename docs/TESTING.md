# Testes

Contagens da última execução completa (todos passando): **backend 143** (`mvnw test`), **frontend 65** (`npm test`) e **29 de ponta a ponta** em navegador real (`npm run e2e`).

## Backend (`cd backend && ./mvnw test`, Windows: `mvnw.cmd test`)
| Tipo | Onde | O que prova |
|---|---|---|
| Unidade (Mockito, relógio fixo) | `*ServiceTest`, `ReportCsvTest`, `SmtpChannelTest`, `JwtServiceTest` | regras de empréstimo, multa, reserva, pagamento, isenção; CSV seguro; JWT |
| Segurança de endpoints (`@WebMvcTest`) | `*SecurityTest` | quem recebe 401/403/400, inclusive que corpo inválido não vaza mensagem a quem não tem permissão |
| Fluxo contra PostgreSQL real (Testcontainers) | `*FluxoTest`, `EsquemaPostgresTest` | migrações V1–V6, mapeamento JPA, regras impostas pelo banco, painel e relatórios com números exatos, avisos |
| Concorrência | `EmprestimoFluxoTest`, `ReservaMultaFluxoTest` | 5 balcões no mesmo exemplar = 1 empréstimo; 6 empréstimos do mesmo leitor = 3; 4 pagamentos da mesma multa = 1 |

Os testes de fluxo **precisam do Docker** e são pulados sem ele (o Maven mostra `Skipped`). Um relógio controlável (`MutableClock`) simula atrasos sem esperar. O agendamento é desligado nos testes (`backend/src/test/resources/application.properties`); as rotinas são chamadas à mão.

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

## Ponta a ponta (navegador real)
Playwright (Chromium) contra a **pilha completa em Docker** (nginx + backend + PostgreSQL), numa instância isolada que não toca no seu banco. Dentro da pasta `frontend`:
```
npx playwright install chromium   # só na primeira vez
npm run e2e:up                    # sobe a pilha de teste (portas 3100 e 55433, valores fictícios de e2e/e2e.env)
npm run e2e
npm run e2e:down                  # derruba e apaga os dados de teste
```
Dois projetos: **desktop** (1280×800) e **mobile** (Pixel 5). Um worker só, porque o banco é compartilhado.
- `auth.spec.ts`: senha errada, campos vazios, rota protegida volta para o destino, sair encerra a sessão, token só em `sessionStorage`, **bloqueio após 5 senhas erradas**.
- `staff-flow.spec.ts`: a equipe cadastra autor, obra (a regra da consulta é recusada na tela e depois aceita), leitor, empresta (exemplar de consulta nem aparece) e devolve, tudo pela interface.
- `reader.spec.ts`: leitor sem menu da equipe, "Acesso negado" ao digitar a rota, reserva e cancelamento.
- `security.spec.ts`: CSP e cabeçalhos reais do nginx e da API, sem erro de CSP/console ao navegar por todas as telas, **XSS** (HTML vindo do servidor aparece como texto e não executa).
- `a11y.spec.ts`: **axe-core** (WCAG 2.0/2.1 A e AA) em todas as telas e diálogos, em desktop e celular, com dados reais; salva uma imagem de cada tela em `e2e-shots/`.
- `mobile.spec.ts`: menu recolhível, nenhuma tela rola na horizontal, formulário de obra cabe na tela.

Os testes **não dependem de banco vazio** (verificado: rodadas seguidas na mesma pilha passam). O E2E já pegou defeitos reais que os outros testes não pegavam: saúde do backend derrubada pelo servidor de e-mail, healthcheck do nginx em IPv6, tabela rolável inacessível por teclado e a falta de um filtro por leitor no balcão.

## O que NÃO está coberto
- **Só Chromium.** Firefox e Safari não foram testados.
- **O axe-core encontra só uma parte dos problemas de acessibilidade** (a própria ferramenta avisa que automação cobre uma fração). **Teste manual com leitor de tela (NVDA, VoiceOver) não foi feito.**
- A aparência foi conferida por mim em capturas; não houve revisão de um designer nem teste com usuários.
- **GitHub Actions (`.github/workflows/ci.yml`) foi escrito mas não executado**: não consegui rodá-lo daqui. Os mesmos comandos rodaram localmente, mas o CI real pode revelar diferenças de ambiente.
- E-mail com o provedor real (o caminho SMTP é testado contra o Mailpit), carga e desempenho no navegador (Lighthouse): não testados.
