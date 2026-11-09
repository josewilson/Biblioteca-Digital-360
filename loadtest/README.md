# Teste de carga (k6)

Equipe consultando o sistema: busca de obras (com acento), detalhe de obra, busca de usuários, painel, empréstimos e paginação.
Cada usuário virtual entra uma vez e reaproveita o token (o login tem limite de tentativas por IP de propósito).
Antes da medição, o script cria 10 autores, `BOOKS` obras (2 exemplares cada) e `USERS` usuários.

## Rodar
Com a pilha de pé (ex.: `cd frontend && npm run e2e:up`, que usa a porta 3100 e o administrador de teste), na raiz do projeto:
```
docker run --rm -i -e BASE=http://host.docker.internal:3100 grafana/k6:latest run - < loadtest/k6.js
```
Variáveis: `BASE`, `ADMIN_EMAIL`/`ADMIN_PASSWORD`, `BOOKS` (1000), `USERS` (300), `VUS` (pico, 50) e `THINK` (pausa média entre ações em segundos, 1; use 0.1 para estressar).
Limites do script: menos de 1% de erro e p95 abaixo de 800 ms. **Não rode contra produção**: ele cadastra dados.

Com o `LOGIN_RATE` padrão do nginx (30/min por IP) a entrada dos usuários virtuais pode tomar 429; o ambiente de teste de ponta a ponta usa um limite alto.
