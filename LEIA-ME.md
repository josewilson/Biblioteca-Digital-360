# Biblioteca Digital 360

Sistema Integrado de Gestão Bibliotecária (SIGB) para instituições de ensino: acervo, usuários,
empréstimos, devoluções, reservas, renovações, multas, pagamentos e relatórios.

> **Estado:** em construção. Fases 1 a 5 concluídas (fundação, banco, login, usuários, acervo e circulação: empréstimos, reservas, multas e pagamentos). O restante está no [roadmap](docs/ROADMAP.md).

## O que existe hoje
- Aplicação Spring Boot que sobe e expõe `/actuator/health`.
- Esquema PostgreSQL completo versionado com Flyway (`V1`): usuários, perfis, acervo, exemplares, empréstimos, reservas, multas, pagamentos, notificações e auditoria.
- Regras garantidas pelo banco: exemplar de consulta não pode ser emprestado; exemplar não tem dois empréstimos ativos; uma multa por empréstimo.
- Tratamento central de erros (`ProblemDetail`).
- Login com JWT, perfis, cadastro e desativação de usuários, auditoria de login (veja `docs/SECURITY.md`).
- Acervo: obras com exemplares, autores, editoras e categorias; regra de consulta imposta no cadastro e na baixa (veja `docs/API.md`).
- Balcão: empréstimo, devolução (com multa por atraso) e renovação, com limite de 3 por leitor, bloqueio por atraso ou multa em aberto e proteção contra operações simultâneas (veja `docs/BUSINESS_RULES.md`).
- Reservas com fila (exemplar guardado para o 1º, prazo de retirada, expiração), pagamento de multa com comprovante e isenção autorizada com justificativa.
- Código **legado** da aula (Cap 04): `Livro`, `Leitor`, `Biblioteca`, `CalculadoraDeMulta`, com 11 testes. Será substituído aos poucos.

**Ainda não existe:** telas, relatórios/dashboard, envio real de e-mail, relatórios, Docker da aplicação, CI.

## Tecnologias
Java 21 · Spring Boot 3.5.16 · Spring Security (JWT) · Spring Data JPA · PostgreSQL 16 · Flyway · Actuator · JUnit 5 · Testcontainers · Maven.

## Pré-requisitos
JDK 21 e Docker (para o banco local e para os testes de esquema). O Maven vem pelo wrapper.

## Configuração
```
copy .env.example .env      # edite DB_PASSWORD
```
Variáveis: `JWT_SECRET` (obrigatória, ≥ 32 caracteres), `ADMIN_EMAIL`/`ADMIN_PASSWORD` (cria o 1º SUPER_ADMIN), `DB_URL` (padrão `jdbc:postgresql://localhost:5432/biblioteca`), `DB_USER`, `DB_PASSWORD` (obrigatória, sem padrão).

## Executar
Banco: `docker compose up -d`

Pelo IntelliJ: abra `BibliotecaApplication`, defina `DB_PASSWORD` na configuração de execução e rode.
Pelo terminal (Windows PowerShell): `$env:DB_PASSWORD="sua-senha"; .\mvnw.cmd spring-boot:run`

Programa de exemplo do legado: `App.java`.

## Testes
`mvnw.cmd test`. Os testes `EsquemaPostgresTest` usam Docker e são pulados se ele estiver desligado.

## Regras de negócio principais
Resumo em [docs/BUSINESS_RULES.md](docs/BUSINESS_RULES.md). Esquema em [docs/DATABASE.md](docs/DATABASE.md).

## Contribuição
Leia o [AGENTS.md](AGENTS.md) (vale para pessoas e agentes de IA): convenções, camadas e padrão de commits.
