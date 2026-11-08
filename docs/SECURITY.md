# Segurança (Fase 3)

- Login: `POST /api/v1/auth/login` → JWT (HS256, `JWT_SECRET` ≥ 32 caracteres, validade `JWT_TTL_MINUTES`, padrão 60). Resposta 401 igual para e-mail inexistente e senha errada.
- API stateless; token em `Authorization: Bearer`. Perfis na claim `roles`. **Logout** = o cliente descarta o token (não há revogação no servidor; usuário desativado continua com token válido até expirar).
- Senhas com BCrypt. Hash e CPF nunca saem na API.
- Cadastro de usuários (`/api/v1/users`): SUPER_ADMIN, ADMIN_INSTITUTION, LIBRARIAN, ASSISTANT. Desativar: só os dois administradores.
  Escalada bloqueada: bibliotecário/auxiliar só cadastram TEACHER, STUDENT, EMPLOYEE, VISITOR; só SUPER_ADMIN cria SUPER_ADMIN.
- Auditoria: `LOGIN_OK`, `LOGIN_FAILED` (com e-mail tentado e IP), `USER_CREATED`, `USER_DEACTIVATED` em `audit_logs`.
- Primeiro SUPER_ADMIN: `ADMIN_EMAIL`/`ADMIN_PASSWORD`, usados só se não houver usuário.
- **Pendente:** recuperação de senha (depende do canal de e-mail, Fase 6), bloqueio por tentativas, permissões finas por `permissions` (hoje só por perfil), edição de usuário.

## Autorização por URL (Fase 4)
`SecurityConfig` aplica as regras por caminho **antes** da validação do corpo: sem isso, um usuário sem permissão que enviasse JSON inválido receberia 400 (com mensagens de validação) em vez de 403.
Consulta ao acervo (`GET`): qualquer autenticado. Escrita em acervo e `/users`: equipe. Os `@PreAuthorize` ficam como segunda barreira.

## Circulação (Fase 5)
- Reservas, multas e pagamentos de outros leitores: só equipe. O leitor usa `/me/**` (as próprias reservas, multas, empréstimos); cancelar reserva alheia responde 404.
- Isenção de multa (`/fines/*/waive`): só SUPER_ADMIN, ADMIN_INSTITUTION e LIBRARIAN; auxiliar recebe pagamento mas não isenta. Regra por URL, antes da validação do corpo.
- Auditoria nova: `LOAN_*`, `RESERVATION_*`, `FINE_PAID`, `FINE_WAIVED`, `COPY_*`.

## Painel, relatórios e notificações (Fase 6)
- `/dashboard` e `/reports/**`: só a equipe (regra por URL + `@PreAuthorize`). O relatório `users` traz só totais por perfil; `overdue` inclui e-mail do leitor para contato.
- SQL dos relatórios é fixo; o usuário escolhe só o relatório (lista fechada) e as datas. Slug desconhecido: 404.
- **CSV:** textos que começam com `=`, `+`, `-` ou `@` recebem um apóstrofo na frente (evita que o Excel/Sheets execute como fórmula); números não são alterados.
- O log de notificações mascara o e-mail (`a***@dominio`). O SMTP usa credenciais só por variável de ambiente.

## Revisão de segurança da Fase 7
Revisão feita sobre o código e sobre a pilha rodando em containers. O que foi **achado e corrigido**:

| Achado | Correção | Como se comprova |
|---|---|---|
| Força bruta no login (só havia registro das falhas) | 5 falhas em 15 min para o mesmo e-mail bloqueiam o login (HTTP 429), mesmo com a senha certa; tentativa bloqueada não prolonga o bloqueio | `AuthServiceTest`, `FluxoDeLoginTest`, `auth.spec.ts` |
| E-mail inexistente respondia mais rápido que senha errada (revelava quem tem conta) | confere sempre uma senha (hash de mentira) | `AuthServiceTest` |
| Capa da obra aceitava qualquer texto (`javascript:`, `data:`) | só `http://` ou `https://`, no servidor e no formulário | `CreateBookRequestValidationTest`, `schemas.test.ts` |
| API sem CSP | `default-src 'none'; frame-ancestors 'none'`, `Referrer-Policy: no-referrer` | `UserControllerSecurityTest`, `security.spec.ts` |
| Site sem cabeçalhos de proteção | nginx: CSP restrita (scripts só do próprio site), `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`; versão do nginx oculta | `security.spec.ts` (navegador real) |
| Saúde da aplicação dependia do servidor de e-mail (queda de SMTP marcaria tudo como fora do ar) | indicador de e-mail da saúde desligado | pilha sobe `healthy` no Docker |
| Banco publicado na rede no compose | publica só em `127.0.0.1` | `docker-compose.yml` |
| Backend rodando como root no container | roda como usuário comum (uid 100); a porta 8080 nem é publicada no host | verificado no container (`id`). O processo-mestre do nginx oficial ainda inicia como root (os workers não) |

**Verificado e sem problema:** SQL injection (JPQL parametrizado; SQL de relatórios fixo e lista fechada de relatórios); XSS (React escapa; teste no navegador injeta `<img onerror>` e confirma que vira texto); IDOR (cada leitor só vê o que é seu em `/me`; reserva alheia responde 404); CSV injection (neutralizada); mass assignment (DTOs); segredos (nenhum no código de produção; `JWT_SECRET` sem padrão); `npm install` do frontend reporta 0 vulnerabilidades (o CI roda `npm audit` nas dependências de produção); Actuator expõe só `health` e `info`, e o nginx nem repassa `/actuator`.

**Continua em aberto (conhecido, não corrigido):**
- **Bloqueio por e-mail** permite a alguém trancar a conta de outra pessoa por 15 min (aceito: a alternativa é pior).
- **Token em `sessionStorage`** e sem revogação no servidor (usuário desativado mantém acesso até o token vencer, 60 min).
- **Sem HTTPS/HSTS no projeto**: precisa de proxy com TLS na frente (ver `DEPLOYMENT.md`).
- **Sem limite de requisições por IP** no nginx; só o bloqueio de login.
- **Política de senha** só exige 8 caracteres (sem lista de senhas comuns).
- **Imagens de capa externas** são carregadas direto do endereço informado (o navegador do leitor contata esse servidor).
- **Ações do GitHub** usam tags de versão (`@v4`), não SHA fixo.
- Não foi feito teste de intrusão, análise estática (SAST) nem varredura de dependências do backend (Maven); o Dependabot está configurado para avisar.
