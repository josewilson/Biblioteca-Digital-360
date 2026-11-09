# Biblioteca Digital 360 — instruções para agentes de IA

**Todo agente deve ler este arquivo inteiro antes de alterar qualquer coisa.**
`CLAUDE.md` apenas aponta para cá. Se algo aqui contradisser o código, avise o usuário em vez de adivinhar.

## 1. Identidade
Biblioteca Digital 360: Sistema Integrado de Gestão Bibliotecária (SIGB) para instituições de ensino.
Meta: projeto de portfólio com qualidade profissional. Origem: projeto de aula "Biblioteca do Bairro" (Cap 04, SOLID),
que continua no código como **legado** (pacote raiz `br.com.biblioteca`) até ser substituído pelos módulos novos.

## 2. Estado real (atualize ao concluir cada fase)
- **Feito:** Fases 1 a 6 e a maior parte da Fase 7: revisão de segurança (docs/SECURITY.md), revisão de desempenho (docs/PERFORMANCE.md), Docker (backend, site e compose), testes de ponta a ponta com Playwright + axe, GitHub Actions e Dependabot (rodando no GitHub). Depois: LGPD (exportar e anonimizar dados, `/privacidade`), troca e redefinição de senha, revogação imediata de sessão, edição de obra, exemplar, usuário e cadastros de apoio, regras de empréstimo pela tela, busca sem acento com índices. Esquema em V1-V9.
- **Não feito:** OpenAPI/Swagger, recuperação de senha por e-mail, HTTPS embutido, teste de carga, teste manual com leitor de tela, exclusão de obra, upload de capa.
- Nunca documente como pronto o que não existe.

## 3. Stack e versões (as de `backend/pom.xml` mandam)
Java 21 · Spring Boot 3.5.16 (Web, Validation, Data JPA, Actuator, Security + OAuth2 resource server para JWT) · PostgreSQL 16 · Flyway · JUnit 5 · Testcontainers · Maven Wrapper.
Frontend (`frontend/`): React 19, TypeScript 7 (strict), Vite 8, React Router 8, TanStack Query 5, React Hook Form 7 + Zod 4, Tailwind CSS 4, Lucide, Recharts 3, Vitest 5 + Testing Library. Versões exatas em `frontend/package.json`.
Planejado, ainda **não** no projeto: springdoc OpenAPI, Mockito explícito (já vem no starter de teste). Dependência nova só com finalidade concreta, justificada ao usuário.

## 4. Estrutura
```
backend/src/main/java/br/com/biblioteca/
  BibliotecaApplication.java   ponto de entrada Spring
  shared/error/                exceções e GlobalExceptionHandler
  acervo/ user/ security/ audit/ loan/ notification/ report/ shared/   módulos implementados (loan = circulação; report = painel e relatórios)
  (legado) Livro, Leitor, Biblioteca, CalculadoraDeMulta, App ...   substituir por módulos
  <módulo>/                    futuro: acervo, usuario, emprestimo, reserva, multa, ...
backend/src/main/resources/db/migration/   Vnn__descricao.sql
docs/                              documentação complementar
frontend/                          aplicação React (src/lib = API/auth/formatação, src/components, src/pages, src/schemas.ts)
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
- **Repository:** só acesso a dados (Spring Data). Sem regra. Exceção: `report`, `notification` e `user/PersonalDataStore` (LGPD) usam SQL (`JdbcTemplate`) para agregações de leitura e para a fila de avisos; SQL sempre fixo, nunca montado com texto do usuário.

## 8. Entidades, DTOs, endpoints
- Entidade JPA nunca sai pela API; use DTO (`record` permitido em DTOs).
- Entradas validadas com Bean Validation; saídas sem dados sensíveis (hash de senha, CPF salvo necessidade).
- REST: `/api/v1/<recurso-no-plural>`, verbos HTTP corretos, paginação em listas, 201 + `Location` na criação.

## 9. Erros e validação
- `BusinessRuleException` → 422; `ResourceNotFoundException` → 404; validação → 400. Formato `ProblemDetail` (RFC 7807).
- Tradução para HTTP só em `GlobalExceptionHandler`. Nunca vazar stack trace.

## 10. Segurança
- Sem segredo no código: tudo por variável de ambiente (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_*`, `spring.mail.*`). `DB_PASSWORD` e `JWT_SECRET` **não têm valor padrão** (a aplicação não sobe sem eles). `.env` e `.env.example` ficam fora do Git (decisão do usuário): as variáveis estão descritas no LEIA-ME.md.
- Perfis: SUPER_ADMIN, ADMIN_INSTITUTION, LIBRARIAN, ASSISTANT, TEACHER, STUDENT, EMPLOYEE, VISITOR. Autorização na Fase 3.
- LGPD: coletar só o necessário (CPF opcional). Exportar (`/me/data`, `/users/{id}/export`) e anonimizar (`/users/{id}/anonymize`) estão em `user/PersonalDataStore`: **dado pessoal novo em qualquer tabela entra na exportação e na limpeza de lá**. Detalhes em `docs/LGPD.md`.

## 11. Banco
- Mudança de esquema **só** por nova migração `V<n>__descricao.sql`. Nunca edite migração já aplicada.
- `ddl-auto: validate`. Constraints no banco quando possível (unicidade, FK composta, CHECK).

## 12. Testes
- Backend: `cd backend && ./mvnw test` (Windows: `mvnw.cmd test`). Frontend: `cd frontend && npm test` e `npm run build`. Só diga que passou se rodou e viu o resultado.
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

## 15. Frontend (`frontend/`)
- **Autorização é do servidor.** A interface esconde menus e botões por perfil só por conveniência (`RequireRole`, `useAuth`); nunca trate isso como proteção. O JWT é lido apenas para id, perfis e validade.
- **Sessão:** o token traz `ver` (conferido no banco a cada requisição) e `mustChangePassword` (sem perfis até trocar a senha). Quem troca a senha assume o token novo (`adoptToken`).
- **Token** em `sessionStorage` (`lib/api.ts`). Proibido `dangerouslySetInnerHTML`, `innerHTML`, `eval` ou qualquer HTML montado com texto da API: o React já escapa o texto, e é isso que mantém o risco de XSS baixo.
- **API:** toda chamada passa por `api()` (`lib/api.ts`), que manda o token, traduz o `ProblemDetail` em `ApiError` e derruba a sessão num 401. Tipos em `lib/types.ts` espelham os DTOs de `docs/API.md`: mudou o DTO, mude o tipo.
- **Dados do servidor** com TanStack Query (nada de `useEffect` + `fetch`); invalide as chaves afetadas após cada mutação.
- **Formulários** com React Hook Form + Zod (`schemas.ts`). A validação do navegador é só para dar resposta rápida: o servidor valida de novo e sua mensagem é mostrada (`ErrorAlert`).
- **Dinheiro** chega do servidor como número em reais e só é formatado para exibição (`formatMoney`). Nenhuma conta com dinheiro no navegador.
- **Acessibilidade (mínimo obrigatório):** todo campo com `<Field>` (rótulo ligado por id, erro anunciado), diálogos nativos `<dialog>`, estado sempre com texto além da cor, foco visível, tabelas com `caption` e `scope`, gráficos com `aria-label` descrevendo os números, link "Pular para o conteúdo".
- **Operação crítica** (devolver, baixar exemplar, cancelar reserva, desativar usuário, isentar multa) sempre com `ConfirmDialog`/diálogo.
- **Em desenvolvimento** o Vite repassa `/api` para `localhost:8080` (sem CORS). Não adicione `@CrossOrigin` no backend para isso.
- Sem dependência de UI nova sem necessidade: os componentes de `components/ui.tsx` são próprios (Tailwind) em vez de shadcn/ui, que exige gerar código no projeto.

## 16. Design da interface
- **Antes de mexer no visual, leia `docs/DESIGN.md`** (direção "ficha de empréstimo", tokens, tipografia, revisão contra o genérico). Mudança visual que contrarie o documento exige atualizar o documento junto.
- A skill `frontend-design` (plugin oficial da Anthropic, instalado no escopo do usuário) é a referência de método: plano de design → revisão contra o genérico → construir → **olhar as capturas** (`frontend/e2e-shots/`, geradas por `npm run e2e`) e criticar.
- Cores e fontes vêm dos tokens em `frontend/src/index.css` (`@theme`). Não use cores fora dos tokens, nem fontes de terceiros (a CSP só aceita o próprio site; as fontes são pacotes `@fontsource`).
- Título de obra sempre em serifa (`workTitleClass`); prazo de empréstimo sempre com `DueStamp`; estado sempre com texto, nunca só cor; sem animação que ninguém pediu (gráficos têm `isAnimationActive={false}`).
- Toda tela nova entra em `e2e/a11y.spec.ts` (axe, desktop e celular) antes de ser dada como pronta.
