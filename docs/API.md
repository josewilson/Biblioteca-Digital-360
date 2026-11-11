# API (v1)

Autenticação: `Authorization: Bearer <token>` (veja SECURITY.md). Erros em `ProblemDetail`: 400 validação, 401, 403, 404, 422 regra de negócio.
Perfis "equipe": SUPER_ADMIN, ADMIN_INSTITUTION, LIBRARIAN, ASSISTANT.

| Método e caminho | Quem | O que faz |
|---|---|---|
| `POST /auth/login` | público | devolve o token |
| `POST /auth/forgot` | público | pede o link de redefinição por e-mail (`email`); **204 sempre**, exista a conta ou não |
| `POST /auth/reset` | público | define a senha nova com o token do e-mail (`token`, `newPassword`); 204, ou 422 se o link for inválido, expirado ou já usado |
| `POST /auth/password` | autenticado | troca a própria senha (`currentPassword`, `newPassword`) e devolve token novo; os anteriores caem |
| `POST/GET /users?q=`, `GET /users/{id}` | equipe | cadastra / lista ou busca por nome (sem acento), e-mail, matrícula / consulta |
| `GET /users/lookup?ids=1,2` | equipe | vários usuários por id (máx. 100) |
| `PUT /users/{id}` | equipe, só quem ela pode administrar | edita dados e perfis; `cpf`: null mantém, vazio apaga |
| `POST /users/{id}/password` | equipe, só quem ela pode administrar | define senha temporária; o dono troca ao entrar |
| `DELETE /users/{id}`, `POST /users/{id}/reactivate` | administradores | desativa / reativa (não a própria conta) |
| `GET /users/{id}/export` | administradores | baixa todos os dados pessoais do usuário (JSON, inclui CPF); fica na auditoria |
| `POST /users/{id}/anonymize` | administradores | LGPD: apaga a identidade (irreversível). 422 com empréstimo, multa ou reserva em aberto (ver `LGPD.md`) |
| `GET /privacy` | público | contato de privacidade da instituição (`PRIVACY_CONTACT`), mostrado em `/privacidade` |
| `GET /me/data` | autenticado | baixa os próprios dados pessoais (JSON) |
| `POST /notifications/test` | administradores | manda um e-mail de teste ao próprio administrador; 422 com o motivo se o provedor recusar |
| `GET /policies`, `PUT /policies/{perfil}` | administradores | regras de empréstimo e multa por dia, de cada perfil que empresta |
| `POST /authors`, `/publishers`, `/categories` | equipe | cadastra (editora e categoria: nome único) |
| `PUT /authors/{id}`, `/publishers/{id}`, `/categories/{id}` | equipe | renomeia |
| `GET /authors`, `/publishers`, `/categories` | autenticado | lista por nome |
| `POST /books` | equipe | cadastra obra **com exemplares** (≥ 1 `CONSULTA` e ≥ 1 `CIRCULANTE`) |
| `GET /books?q=&page=&size=` | autenticado | busca paginada: trecho do título ou autor (sem acento), ISBN ou código de exemplar exatos |
| `PUT /books/{id}` | equipe | edita os dados da obra (exemplares não mudam aqui) |
| `GET /books/{id}` | autenticado | obra com autores, categorias e exemplares |
| `GET /copies?q=&status=&kind=&condition=&categoryId=&page=&size=` | equipe | lista paginada de exemplares (um por linha, ordenados por título e código) com título, ISBN, editora, autores e categorias da obra; `q` casa com título, autor, editora, categoria, ISBN (sem hífen), código e localização, sem acento e em trecho; os filtros se combinam; filtro inválido responde 400 |
| `POST /books/{id}/copies` | equipe | acrescenta exemplar |
| `PUT /copies/{id}` | equipe | edita código, estado e localização (o tipo não muda) |
| `PATCH /copies/{id}/status` | equipe | `DISPONIVEL`, `PERDIDO` ou `BAIXADO`. Recusa baixa do único exemplar de consulta ativo |

Exemplo de cadastro de obra:
```json
{"title":"Dom Casmurro","isbn":"9788535910663","publicationYear":1899,"publisherId":1,"authorIds":[1],"categoryIds":[1],
 "copies":[{"barcode":"DC-1","kind":"CONSULTA","location":"Estante A"},{"barcode":"DC-2","kind":"CIRCULANTE","condition":"NOVO"}]}
```
**Dados do livro por ISBN:** `GET /books/isbn/{isbn}` (equipe) devolve uma **sugestão** para o formulário "Nova obra" e **não grava nada**. O ISBN (10 ou 13, com ou sem hífen) é normalizado para 13 dígitos; verificador errado responde 400. Se já existe obra com o ISBN (em qualquer formato), responde `alreadyRegistered: true` e `existingBookId` sem consultar ninguém. Senão consulta a BrasilAPI e completa com a Open Library (o primeiro provedor com o campo ganha; `sources` diz de onde veio cada um). Editora, autores e categorias vêm como `{id, name}`: com `id` já existem no cadastro; sem `id` são só nomes sugeridos (no máximo 10 autores e 5 categorias novas). 404 se nenhuma base conhece o livro; 503 se os provedores não responderam (um provedor fora do ar não derruba a busca). Edição e idioma só aparecem se algum provedor trouxer. Autores: se a BrasilAPI não os traz, a Open Library fornece, com no máximo 5 chamadas extras por livro (uma por autor, e uma pela obra quando a edição não lista autores); se essa etapa falhar, a sugestão vem sem autores em vez de dar erro.
**Busca de capa por ISBN:** `POST /books/{id}/cover/lookup` (equipe) consulta a Open Library com o ISBN **já salvo** na obra e grava a imagem como se tivesse sido enviada (mesmas validações, regravada em JPEG). 422 com mensagem clara se a obra não tem ISBN válido, se o serviço não tem capa (`Nenhuma capa encontrada…`) ou se está fora do ar.
**Capa:** `PUT /books/{id}/cover` (equipe, `multipart/form-data`, campo `file`, JPEG ou PNG até 2 MB e 8000 px de lado) e `DELETE /books/{id}/cover` (equipe) devolvem a obra com `coverVersion` (nulo = sem capa enviada). `GET /books/{id}/cover?size=thumb|full&v=<coverVersion>` é **público** (a tag `<img>` não envia token) e devolve JPEG com cache longo; 404 se não há capa. O servidor regrava a imagem em JPEG (full até 600x900, miniatura até 160x240): o arquivo enviado nunca é guardado nem servido como veio. `coverUrl` (endereço externo) continua valendo quando não há capa enviada.
**Excluir obra:** `DELETE /books/{id}` (administrador) responde 204 e apaga a obra com seus exemplares e a capa. Obra com empréstimo ou reserva no histórico, mesmo devolvido, responde 422: baixe os exemplares para tirá-la de circulação. Pendente: OpenAPI/Swagger. Tamanho máximo de página: 100. Conflito de unicidade em gravações simultâneas responde 409.
**Especificação OpenAPI:** `GET /api/v1/openapi` (JSON, só administrador, com o token no cabeçalho Authorization) lista todos os endpoints e o esquema Bearer; abre no Swagger Editor, Postman ou Insomnia. Não há Swagger UI: a CSP da API (`default-src 'none'`) e a do site bloqueariam a página, e liberá-las abriria superfície sem necessidade.

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
