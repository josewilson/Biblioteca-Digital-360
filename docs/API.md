# API (v1)

Autenticação: `Authorization: Bearer <token>` (veja SECURITY.md). Erros em `ProblemDetail`: 400 validação, 401, 403, 404, 422 regra de negócio.
Perfis "equipe": SUPER_ADMIN, ADMIN_INSTITUTION, LIBRARIAN, ASSISTANT.

| Método e caminho | Quem | O que faz |
|---|---|---|
| `POST /auth/login` | público | devolve o token |
| `POST /auth/password` | autenticado | troca a própria senha (`currentPassword`, `newPassword`) e devolve token novo; os anteriores caem |
| `POST/GET /users?q=`, `GET /users/{id}` | equipe | cadastra / lista ou busca por nome (sem acento), e-mail, matrícula / consulta |
| `GET /users/lookup?ids=1,2` | equipe | vários usuários por id (máx. 100) |
| `PUT /users/{id}` | equipe, só quem ela pode administrar | edita dados e perfis; `cpf`: null mantém, vazio apaga |
| `POST /users/{id}/password` | equipe, só quem ela pode administrar | define senha temporária; o dono troca ao entrar |
| `DELETE /users/{id}`, `POST /users/{id}/reactivate` | administradores | desativa / reativa (não a própria conta) |
| `GET /policies`, `PUT /policies/{perfil}` | administradores | regras de empréstimo e multa por dia, de cada perfil que empresta |
| `POST /authors`, `/publishers`, `/categories` | equipe | cadastra (editora e categoria: nome único) |
| `PUT /authors/{id}`, `/publishers/{id}`, `/categories/{id}` | equipe | renomeia |
| `GET /authors`, `/publishers`, `/categories` | autenticado | lista por nome |
| `POST /books` | equipe | cadastra obra **com exemplares** (≥ 1 `CONSULTA` e ≥ 1 `CIRCULANTE`) |
| `GET /books?q=&page=&size=` | autenticado | busca paginada: trecho do título ou autor (sem acento), ISBN ou código de exemplar exatos |
| `PUT /books/{id}` | equipe | edita os dados da obra (exemplares não mudam aqui) |
| `GET /books/{id}` | autenticado | obra com autores, categorias e exemplares |
| `POST /books/{id}/copies` | equipe | acrescenta exemplar |
| `PUT /copies/{id}` | equipe | edita código, estado e localização (o tipo não muda) |
| `PATCH /copies/{id}/status` | equipe | `DISPONIVEL`, `PERDIDO` ou `BAIXADO`. Recusa baixa do único exemplar de consulta ativo |

Exemplo de cadastro de obra:
```json
{"title":"Dom Casmurro","isbn":"9788535910663","publicationYear":1899,"publisherId":1,"authorIds":[1],"categoryIds":[1],
 "copies":[{"barcode":"DC-1","kind":"CONSULTA","location":"Estante A"},{"barcode":"DC-2","kind":"CIRCULANTE","condition":"NOVO"}]}
```
Pendente: capa por upload (hoje só `coverUrl`), excluir obra, OpenAPI/Swagger. Tamanho máximo de página: 100. Conflito de unicidade em gravações simultâneas responde 409.

## Balcão (Fase 5, parte 1)
| Método e caminho | Quem | O que faz |
|---|---|---|
| `POST /loans` `{userId, copyId}` | equipe | empresta (422 se violar alguma regra) |
| `POST /loans/{id}/return` | equipe | devolve; resposta `{loan, fine}` (`fine` nulo sem multa) |
| `POST /loans/{id}/renew` | equipe | renova |
| `GET /loans?userId=&activeOnly=` , `GET /loans/{id}` | equipe | consulta (paginado) |
| `GET /me/loans?activeOnly=` | qualquer autenticado | só os próprios empréstimos |

## Reservas, multas e pagamentos (Fase 5, parte 2)
| Método e caminho | Quem | O que faz |
|---|---|---|
| `POST /reservations` `{userId, bookId}` | equipe | reserva para um leitor |
| `POST /reservations/{id}/cancel` | equipe | cancela |
| `GET /reservations?userId=&status=` | equipe | lista (paginado; `queuePosition` para `NA_FILA`) |
| `POST /me/reservations` `{bookId}` | autenticado | o leitor reserva para si |
| `POST /me/reservations/{id}/cancel`, `GET /me/reservations` | autenticado | só as próprias |
| `GET /fines?userId=&status=` | equipe | lista multas |
| `POST /fines/{id}/payments` `{method}` | equipe | paga o valor integral (201 + comprovante) |
| `POST /fines/{id}/waive` `{reason}` | admins e bibliotecário | isenta, com justificativa |
| `GET /payments/{id}` | equipe | comprovante |
| `GET /me/fines?status=` | autenticado | as próprias multas |

## Painel e relatórios (Fase 6, backend) — somente equipe
| Método e caminho | O que faz |
|---|---|
| `GET /dashboard` | indicadores gerenciais (totais, atrasos, reservas, multas, rankings, movimento diário de 30 dias) |
| `GET /reports/{nome}?from=AAAA-MM-DD&to=AAAA-MM-DD&format=json\|csv` | `loans`, `returns`, `overdue`, `fines`, `payments`, `users`, `top-books`, `availability` |

Resposta JSON: `{ "report", "columns": [...], "rows": [[...]], "truncated" }`. Com `format=csv` baixa `relatorio-{nome}.csv` (UTF-8 com BOM).
Erros: 404 relatório inexistente; 422 período invertido, maior que 366 dias ou formato inválido; 400 data malformada; 403 para leitores.
