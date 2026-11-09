# Biblioteca Digital 360

Sistema Integrado de Gestão Bibliotecária (SIGB) para instituições de ensino: acervo, usuários,
empréstimos, devoluções, reservas, renovações, multas, pagamentos e relatórios.

> **Estado:** em construção. Fases 1 a 6 concluídas (fundação, banco, login, usuários, acervo, circulação, painel, relatórios, notificações e interface web). O restante está no [roadmap](docs/ROADMAP.md).

## O que existe hoje
- Aplicação Spring Boot que sobe e expõe `/actuator/health`.
- Esquema PostgreSQL completo versionado com Flyway (`V1` a `V6`): usuários, perfis, acervo, exemplares, empréstimos, reservas, multas, pagamentos, notificações e auditoria.
- Regras garantidas pelo banco: exemplar de consulta não pode ser emprestado; exemplar não tem dois empréstimos ativos; uma multa por empréstimo.
- Tratamento central de erros (`ProblemDetail`).
- Login com JWT, perfis, cadastro e desativação de usuários, auditoria de login (veja `docs/SECURITY.md`).
- Acervo: obras com exemplares, autores, editoras e categorias; regra de consulta imposta no cadastro e na baixa (veja `docs/API.md`).
- Balcão: empréstimo, devolução (com multa por atraso) e renovação, com limite de 3 por leitor, bloqueio por atraso ou multa em aberto e proteção contra operações simultâneas (veja `docs/BUSINESS_RULES.md`).
- Reservas com fila (exemplar guardado para o 1º, prazo de retirada, expiração), pagamento de multa com comprovante e isenção autorizada com justificativa.
- Painel gerencial e 8 relatórios (JSON ou CSV) para a equipe; avisos por e-mail (canal configurável) para empréstimo, vencimento, atraso, reserva, multa e pagamento.
- Interface web (pasta `frontend/`): login, painel com gráficos, acervo, balcão (empréstimo, devolução, renovação), reservas, multas (pagamento com comprovante e isenção), relatórios com CSV, usuários, cadastros de apoio e área do próprio leitor. Menus e botões mudam conforme o perfil.

**Ainda não existe:** OpenAPI/Swagger, HTTPS embutido no site (existe um proxy opcional, `docker-compose.tls.yml`, ainda não testado com domínio público), exclusão de obra e envio de imagem de capa.

## Tecnologias
Backend: Java 21 · Spring Boot 4.1.2 · Spring Security (JWT) · Spring Data JPA · PostgreSQL 16 · Flyway · Actuator · JUnit 5 · Testcontainers · Maven.
Frontend: React 19 · TypeScript 7 (strict) · Vite 8 · React Router 8 · TanStack Query 5 · React Hook Form + Zod 4 · Tailwind CSS 4 · Recharts 3 · Lucide · Vitest 5 + Testing Library. Versões exatas em `backend/pom.xml` e `frontend/package.json`.

## Pré-requisitos
JDK 21, Docker (banco local e testes) e Node.js (testado com a versão 24; o Vite 8 pede Node 20.19+ ou 22.12+). O Maven vem pelo wrapper.

## Configuração
```
# crie na raiz um arquivo .env (fica só na sua máquina: o Git ignora .env e .env.example)
DB_USER=biblioteca
DB_PASSWORD=escolha-uma-senha
JWT_SECRET=texto-aleatorio-com-32-ou-mais-caracteres
ADMIN_EMAIL=seu-email@exemplo.com      # só no 1º start: cria o SUPER_ADMIN
ADMIN_PASSWORD=senha-do-administrador  # só no 1º start (mínimo 8 caracteres)
```
Opcionais no `.env`: `WEB_PORT`, `WEB_BIND`, `DOMAIN` (HTTPS) e `BACKUP_KEEP_DAYS` (ver [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)). Variáveis: `JWT_SECRET` (obrigatória, ≥ 32 caracteres), `DB_PASSWORD` (obrigatória, **sem valor padrão**), `ADMIN_EMAIL`/`ADMIN_PASSWORD` (cria o 1º SUPER_ADMIN), `DB_URL` (padrão `jdbc:postgresql://localhost:5433/biblioteca`; a porta padrão fica em `application.yml`) e `DB_USER` (padrão `biblioteca`). O `docker compose up -d` publica o banco na mesma porta padrão (mude com `DB_PORT`) e só para a própria máquina (127.0.0.1).

## Executar
Banco: `docker compose up -d`

A aplicação **não lê o `.env` sozinha** (só o `docker compose` lê): informe as variáveis na configuração de execução do IntelliJ (campo *Environment variables*) ou no terminal.

Pelo IntelliJ: abra `BibliotecaApplication`, defina `DB_PASSWORD` e `JWT_SECRET` (e `ADMIN_EMAIL`/`ADMIN_PASSWORD` no primeiro start) e rode.
Pelo terminal (Windows PowerShell): `$env:DB_PASSWORD="sua-senha"; $env:JWT_SECRET="texto-com-32-ou-mais-caracteres"; cd backend; .\mvnw.cmd spring-boot:run`

## Aplicação completa em Docker
Banco, backend e site juntos, com um só comando (detalhes, HTTPS e backup em [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)):
```
docker compose --profile full up -d --build
```
Depois abra `http://localhost:3000`. É preciso ter `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_EMAIL` e `ADMIN_PASSWORD` no `.env` (veja acima). Sem `--profile full`, o compose sobe só o banco, para desenvolvimento.

## Interface web (frontend)
Em outro terminal, com o backend rodando na porta 8080:
```
cd frontend
npm install
npm run dev          # abre em http://localhost:5173
```
O Vite repassa `/api` ao backend (sem CORS). Entre com o administrador criado por `ADMIN_EMAIL`/`ADMIN_PASSWORD` no primeiro start do backend. Para gerar a versão de produção: `npm run build` (saída em `frontend/dist`).

## Testes
Backend: `cd backend` e `mvnw.cmd test`. Os testes contra PostgreSQL (`*FluxoTest`, `EsquemaPostgresTest`) usam Docker e são pulados se ele estiver desligado.
Frontend: `cd frontend` e `npm test` (testes) ou `npm run typecheck`.
Ponta a ponta, em navegador real e com auditoria de acessibilidade: `cd frontend`, `npm run e2e:up` (sobe uma pilha isolada em Docker), `npm run e2e`, `npm run e2e:down`. Na primeira vez: `npx playwright install chromium`. Detalhes em [docs/TESTING.md](docs/TESTING.md).

## Regras de negócio principais
Resumo em [docs/BUSINESS_RULES.md](docs/BUSINESS_RULES.md). Esquema em [docs/DATABASE.md](docs/DATABASE.md). Visão geral em [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md); API em [docs/API.md](docs/API.md); segurança em [docs/SECURITY.md](docs/SECURITY.md).

## Contribuição
Leia o [AGENTS.md](AGENTS.md) (vale para pessoas e agentes de IA): convenções, camadas e padrão de commits.

## Notificações por e-mail
Por padrão os avisos só são **registrados no log** (e-mail mascarado): nada sai da máquina. Para enviar de verdade por SMTP, defina:
`APP_NOTIFICATIONS_CHANNEL=smtp`, `APP_NOTIFICATIONS_FROM=biblioteca@sua-escola.edu` e `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`.
O envio por SMTP real **não foi testado contra um servidor de e-mail** (só com um remetente simulado).
