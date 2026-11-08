# Arquitetura

Monólito modular: um backend Spring Boot + PostgreSQL e uma SPA React que só conversa com a API REST.

```
navegador ──(JWT)──► /api/v1 (Spring MVC) ──► serviços ──► PostgreSQL
   React/Vite                                  │
                                               └─► notificações (fila na tabela notifications)
```

## Backend (`src/main/java/br/com/biblioteca`)
| Pacote | Responsabilidade |
|---|---|
| `security`, `user` | login JWT, perfis, cadastro de usuários, regras de escalada de privilégio |
| `acervo` | obras, exemplares, autores, editoras, categorias; regra "toda obra tem consulta + emprestável" |
| `loan` | circulação: empréstimo, devolução, renovação, reserva (fila), multa, pagamento, isenção |
| `report` | painel e relatórios (SQL de leitura), CSV |
| `notification` | avisos: gravação, entrega por canal (`log`/`smtp`), vencimento e atraso |
| `audit`, `shared` | auditoria, tratamento de erros, relógio, agendamento |
| (raiz) | código **legado** da aula (Cap 04), a ser removido |

Camadas: controller → service (transação e regra) → repository/entidade. DTOs são `record`. Erros viram HTTP só em `GlobalExceptionHandler`.

## Decisões importantes
- **Regras que o banco garante** (não dependem do código acertar): exemplar de consulta não é emprestado (FK composta), um empréstimo ativo por exemplar, uma multa por empréstimo, um pagamento por multa, um exemplar guardado por reserva.
- **Concorrência:** travas de linha (`PESSIMISTIC_WRITE`) em leitor, exemplar, reserva, empréstimo e multa, sempre **na mesma ordem (leitor → exemplar → reserva)** para evitar deadlock. Testado com requisições simultâneas.
- **Dinheiro:** `BigDecimal` / `numeric(10,2)` em reais.
- **Relógio injetado** (`Clock`): "dia de atraso" segue a zona `America/Sao_Paulo`; testes andam no tempo sem esperar.
- **Avisos fora da transação:** a operação grava o aviso na mesma transação; um agendador entrega depois, com tentativas limitadas. Falha de e-mail nunca desfaz empréstimo ou devolução.
- **Relatórios e painel em SQL** (`JdbcTemplate`): são agregações de leitura, não pertencem a uma entidade. SQL fixo; o usuário escolhe só o relatório (lista fechada) e as datas.
- **Sem CORS:** em desenvolvimento o Vite repassa `/api` ao backend; em produção a ideia é servir tudo sob a mesma origem (ainda por fazer, Fase 7).

## Frontend (`frontend/src`)
`lib/` (cliente HTTP, autenticação, formatação, tipos) · `components/` (UI própria em Tailwind, layout, seletores, formulário de obra) · `pages/` (uma por tela) · `schemas.ts` (validação Zod).
- **Dados do servidor:** TanStack Query. **Formulários:** React Hook Form + Zod.
- **Perfil:** a interface lê perfis do JWT só para esconder o que não cabe ao usuário; o servidor é quem autoriza.
- **Token em `sessionStorage`:** some ao fechar a aba. Troca por cookie `httpOnly` exigiria mudança no backend (CSRF, CORS); fica como melhoria possível.
- **Painel carregado sob demanda** (gráficos pesam ~114 kB comprimidos e só a equipe os usa).

## Limites conhecidos
- Revogar um token no servidor não existe: usuário desativado mantém o acesso até o token vencer (60 min por padrão).
- Entrega de avisos pressupõe uma única instância do backend.
- A lista de leitores nas telas de balcão carrega só os 200 primeiros por nome.
