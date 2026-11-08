# Biblioteca Digital 360

Sistema Integrado de Gestão Bibliotecária (SIGB) para instituições de ensino: acervo, usuários,
empréstimos, devoluções, reservas, renovações, multas, pagamentos e relatórios.

> **Estado:** em construção. Fases 1 a 5 e o backend da Fase 6 concluídos (fundação, banco, login, usuários, acervo, circulação, painel, relatórios e notificações). O restante está no [roadmap](docs/ROADMAP.md).

## O que existe hoje
- Aplicação Spring Boot que sobe e expõe `/actuator/health`.
- Esquema PostgreSQL completo versionado com Flyway (`V1`): usuários, perfis, acervo, exemplares, empréstimos, reservas, multas, pagamentos, notificações e auditoria.
- Regras garantidas pelo banco: exemplar de consulta não pode ser emprestado; exemplar não tem dois empréstimos ativos; uma multa por empréstimo.
- Tratamento central de erros (`ProblemDetail`).
- Login com JWT, perfis, cadastro e desativação de usuários, auditoria de login (veja `docs/SECURITY.md`).
- Acervo: obras com exemplares, autores, editoras e categorias; regra de consulta imposta no cadastro e na baixa (veja `docs/API.md`).
- Balcão: empréstimo, devolução (com multa por atraso) e renovação, com limite de 3 por leitor, bloqueio por atraso ou multa em aberto e proteção contra operações simultâneas (veja `docs/BUSINESS_RULES.md`).
- Reservas com fila (exemplar guardado para o 1º, prazo de retirada, expiração), pagamento de multa com comprovante e isenção autorizada com justificativa.
- Painel gerencial e 8 relatórios (JSON ou CSV) para a equipe; avisos por e-mail (canal configurável) para empréstimo, vencimento, atraso, reserva, multa e pagamento.
- Código **legado** da aula (Cap 04): `Livro`, `Leitor`, `Biblioteca`, `CalculadoraDeMulta`, com 11 testes. Será substituído aos poucos.

**Ainda não existe:** telas (frontend React), OpenAPI/Swagger, recuperação de senha, Docker da aplicação, CI.

## Tecnologias
Java 21 · Spring Boot 3.5.16 · Spring Security (JWT) · Spring Data JPA · PostgreSQL 16 · Flyway · Actuator · JUnit 5 · Testcontainers · Maven.

## Pré-requisitos
JDK 21 e Docker (para o banco local e para os testes de esquema). O Maven vem pelo wrapper.

## Configuração
```
# crie na raiz um arquivo .env (fica só na sua máquina: o Git ignora .env e .env.example)
DB_USER=biblioteca
DB_PASSWORD=escolha-uma-senha
JWT_SECRET=texto-aleatorio-com-32-ou-mais-caracteres
ADMIN_EMAIL=seu-email@exemplo.com      # só no 1º start: cria o SUPER_ADMIN
ADMIN_PASSWORD=senha-do-administrador  # só no 1º start (mínimo 8 caracteres)
```
Variáveis: `JWT_SECRET` (obrigatória, ≥ 32 caracteres), `ADMIN_EMAIL`/`ADMIN_PASSWORD` (cria o 1º SUPER_ADMIN), `DB_URL` (padrão `jdbc:postgresql://localhost:5432/biblioteca`), `DB_USER`, `DB_PASSWORD` (obrigatória, sem padrão).

## Executar
Banco: `docker compose up -d`

A aplicação **não lê o `.env` sozinha** (só o `docker compose` lê): informe as variáveis na configuração de execução do IntelliJ (campo *Environment variables*) ou no terminal.

Pelo IntelliJ: abra `BibliotecaApplication`, defina `DB_PASSWORD` e `JWT_SECRET` (e `ADMIN_EMAIL`/`ADMIN_PASSWORD` no primeiro start) e rode.
Pelo terminal (Windows PowerShell): `$env:DB_PASSWORD="sua-senha"; $env:JWT_SECRET="texto-com-32-ou-mais-caracteres"; .\mvnw.cmd spring-boot:run`

Programa de exemplo do legado: `App.java`.

## Testes
`mvnw.cmd test`. Os testes `EsquemaPostgresTest` usam Docker e são pulados se ele estiver desligado.

## Regras de negócio principais
Resumo em [docs/BUSINESS_RULES.md](docs/BUSINESS_RULES.md). Esquema em [docs/DATABASE.md](docs/DATABASE.md).

## Contribuição
Leia o [AGENTS.md](AGENTS.md) (vale para pessoas e agentes de IA): convenções, camadas e padrão de commits.

## Notificações por e-mail
Por padrão os avisos só são **registrados no log** (e-mail mascarado): nada sai da máquina. Para enviar de verdade por SMTP, defina:
`APP_NOTIFICATIONS_CHANNEL=smtp`, `APP_NOTIFICATIONS_FROM=biblioteca@sua-escola.edu` e `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`.
O envio por SMTP real **não foi testado contra um servidor de e-mail** (só com um remetente simulado).
