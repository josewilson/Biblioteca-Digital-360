# Biblioteca Digital 360 — instruções para agentes de IA

**Todo agente deve ler este arquivo inteiro antes de alterar qualquer coisa.**
`CLAUDE.md` apenas aponta para cá. Se algo aqui contradisser o código, avise o usuário em vez de adivinhar.

## 1. Identidade
Biblioteca Digital 360: Sistema Integrado de Gestão Bibliotecária (SIGB) para instituições de ensino.
Meta: projeto de portfólio com qualidade profissional. Origem: projeto de aula "Biblioteca do Bairro" (Cap 04, SOLID),
que continua no código como **legado** (pacote raiz `br.com.biblioteca`) até ser substituído pelos módulos novos.

## 2. Estado real (atualize ao concluir cada fase)
- **Feito:** Fases 1 a 4 e Fase 5 completa (empréstimo, devolução, renovação, reservas com fila, multa, pagamento, isenção). Esquema em V1-V5.
- **Não feito:** relatórios, dashboard, envio real de notificações (e-mail), recuperação de senha, frontend, Docker da aplicação, CI.
- Nunca documente como pronto o que não existe.

## 3. Stack e versões (as de `pom.xml` mandam)
Java 21 · Spring Boot 3.5.16 (Web, Validation, Data JPA, Actuator, Security + OAuth2 resource server para JWT) · PostgreSQL 16 · Flyway · JUnit 5 · Testcontainers · Maven Wrapper.
Planejado, ainda **não** no projeto: springdoc OpenAPI, Mockito explícito (já vem no starter de teste),
React + TypeScript + Vite (Fase 6). Dependência nova só com finalidade concreta, justificada ao usuário.

## 4. Estrutura
```
src/main/java/br/com/biblioteca/
  BibliotecaApplication.java   ponto de entrada Spring
  shared/error/                exceções e GlobalExceptionHandler
  acervo/ user/ security/ audit/ loan/ notification/ shared/   módulos implementados (loan = circulação: empréstimo, reserva, multa, pagamento)
  (legado) Livro, Leitor, Biblioteca, CalculadoraDeMulta, App ...   substituir por módulos
  <módulo>/                    futuro: acervo, usuario, emprestimo, reserva, multa, ...
src/main/resources/db/migration/   Vnn__descricao.sql
docs/                              documentação complementar
```
Cada módulo futuro: `api` (controller, DTO) → `application` (service, regras) → `domain` (entidades, regras puras) → `infra` (repository, adapters).

## 5. Convenções
- Código novo, tabelas e colunas: **inglês** (como no esquema). Mensagens ao usuário e documentação: **português**.
  O legado continua em português até sair.
- Classes `PascalCase`, métodos `camelCase`, tabelas `snake_case` no plural.
- Dinheiro: **reais com 2 casas** (`BigDecimal` no Java, `numeric(10,2)` no banco), por decisão do usuário. Nunca `double`/`float`.
- Datas: `Instant`/`timestamptz`.

## 6. SOLID, Clean Code e padrões
- SRP: uma responsabilidade por classe. OCP/LSP/ISP/DIP: dependa de interface onde há **mais de uma implementação real**
  (ex.: `Notificador` com e-mail e teste). Sem interface de uma implementação só.
- Métodos pequenos, sem duplicar regra, sem lógica de negócio em controller ou entidade.
- Strategy, Factory, Builder, Adapter, Observer, Specification: só com necessidade real.

## 7. Camadas
- **Controller:** recebe/valida DTO, chama service, devolve DTO. Nada de regra.
- **Service:** regra de negócio e transação (`@Transactional`).
- **Domain:** invariantes que não dependem de framework.
- **Repository:** só acesso a dados (Spring Data). Sem regra.

## 8. Entidades, DTOs, endpoints
- Entidade JPA nunca sai pela API; use DTO (`record` permitido em DTOs).
- Entradas validadas com Bean Validation; saídas sem dados sensíveis (hash de senha, CPF salvo necessidade).
- REST: `/api/v1/<recurso-no-plural>`, verbos HTTP corretos, paginação em listas, 201 + `Location` na criação.

## 9. Erros e validação
- `BusinessRuleException` → 422; `ResourceNotFoundException` → 404; validação → 400. Formato `ProblemDetail` (RFC 7807).
- Tradução para HTTP só em `GlobalExceptionHandler`. Nunca vazar stack trace.

## 10. Segurança
- Sem segredo no código: tudo por variável de ambiente (`DB_URL`, `DB_USER`, `DB_PASSWORD`). `.env` fora do Git.
- Perfis: SUPER_ADMIN, ADMIN_INSTITUTION, LIBRARIAN, ASSISTANT, TEACHER, STUDENT, EMPLOYEE, VISITOR. Autorização na Fase 3.
- LGPD: coletar só o necessário (CPF opcional).

## 11. Banco
- Mudança de esquema **só** por nova migração `V<n>__descricao.sql`. Nunca edite migração já aplicada.
- `ddl-auto: validate`. Constraints no banco quando possível (unicidade, FK composta, CHECK).

## 12. Testes
- Rodar: `./mvnw test` (Windows: `mvnw.cmd test`). Só diga que passou se rodou e viu `BUILD SUCCESS`.
- Regra de negócio: teste unitário. Esquema/SQL: `EsquemaPostgresTest` (Testcontainers; **pulado sem Docker**, diga isso no relatório).

## 13. Commits e manutenção
- Commits pequenos, no imperativo, em português: `feat(multa): calcula multa configurável`. Tipos: feat, fix, refactor, docs, test, chore.
- Ao mudar comportamento, atualize `LEIA-ME.md` e `docs/` no mesmo trabalho.
- Preserve o que funciona; não apague classe do legado sem substituta testada.
- Antes de relatar: liste arquivos criados/alterados/removidos, dependências novas e pendências.

## 14. Domínio (regras fixas — detalhes em `docs/BUSINESS_RULES.md`)
- **Obra** (`books`) é diferente de **exemplar** (`book_copies`). Cada exemplar tem código e situação próprios.
- **Toda obra tem no mínimo 2 exemplares, e pelo menos 1 é de consulta** (`CONSULTA`): nunca sai da biblioteca.
  Todos os leitores, inclusive visitante, podem usá-lo no local. Visitante não empresta.
- Um exemplar não tem dois empréstimos ativos; só exemplar `CIRCULANTE` é emprestável (o banco garante).
- Multa: por dia de atraso, valor **configurável** (`fine_rates.daily_rate`), em reais. Padrões iniciais: aluno 1,00, professor 0,50, visitante 2,00.
- Uma multa por empréstimo e um pagamento por multa (sem cobrança duplicada).
- Com multa, o leitor é avisado. `EmailSmtp` (legado) simula envio e demora 2 s.
