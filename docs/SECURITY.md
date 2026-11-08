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
