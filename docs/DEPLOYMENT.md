# Implantação

Pilha completa em containers: **nginx** (serve o site e repassa `/api`) → **backend Spring Boot** → **PostgreSQL 16**.
Só o nginx fica exposto; o backend (porta 8080) e o banco não são alcançáveis de fora.

## Subir
1. Crie um arquivo `.env` ao lado do `docker-compose.yml` com **valores seus** (nunca versione):
   ```
   DB_PASSWORD=<senha forte do banco>
   JWT_SECRET=<texto aleatório com 32+ caracteres>      # ex.: openssl rand -base64 48
   ADMIN_EMAIL=<e-mail do primeiro administrador>
   ADMIN_PASSWORD=<senha do primeiro administrador>      # mínimo 8 caracteres
   WEB_PORT=3000                                         # opcional
   ```
2. `docker compose --profile full up -d --build`
3. Abra `http://localhost:3000` e entre com o administrador.
4. **Depois do primeiro acesso, apague `ADMIN_EMAIL` e `ADMIN_PASSWORD` do `.env`** e recrie o backend (`docker compose --profile full up -d`): só servem para criar o primeiro usuário e são ignorados quando já existe algum.

O backend recusa subir se `JWT_SECRET` tiver menos de 32 caracteres. `DB_PASSWORD` é exigido pelo compose.
`DB_PASSWORD` não tem valor padrão em lugar nenhum: o compose e o `application.yml` exigem que você a defina.

## HTTPS (obrigatório fora da sua máquina)
Este projeto **não inclui** certificado nem terminação TLS. Coloque na frente do nginx um proxy reverso com HTTPS (Caddy, Traefik, nginx do host, balanceador da nuvem) e:
- envie `Strict-Transport-Security` (HSTS) nesse proxy;
- repasse `X-Forwarded-For` e `X-Forwarded-Proto`.
Sem HTTPS, senha e token trafegam em texto puro.

## E-mail
Por padrão os avisos só vão para o log. Para enviar de verdade: `APP_NOTIFICATIONS_CHANNEL=smtp`, `APP_NOTIFICATIONS_FROM` e `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD` no `.env`. **Não testado contra servidor real.**

## Dados e backup
O banco fica no volume `pgdata`. Backup:
```
docker compose exec -T postgres pg_dump -U biblioteca biblioteca | gzip > backup-$(date +%F).sql.gz
```
Restaurar: `gunzip -c backup.sql.gz | docker compose exec -T postgres psql -U biblioteca biblioteca` (num banco vazio).
**Faça backup antes de cada atualização.** Não há rotina automática de backup.

## Atualizar
`git pull` e `docker compose --profile full up -d --build`. As migrações do banco (Flyway) rodam sozinhas ao iniciar o backend. A V9 cria as extensões `pg_trgm` e `unaccent`: o usuário do banco precisa ser dono dele (é o caso no compose). Depois dela, todos precisam entrar de novo. Migrações já aplicadas nunca devem ser editadas.

## Verificar
- `docker compose --profile full ps` (colunas de saúde: healthy).
- `curl http://localhost:3000/` (site) e, de dentro da rede dos containers, `/actuator/health` do backend. O `/actuator/health` **não** é exposto pelo nginx.
- Logs: `docker compose --profile full logs -f backend`.

## Restrições conhecidas
- **Uma instância só do backend.** A entrega de avisos e a expiração de reservas rodam em tarefas agendadas sem trava entre instâncias; duas instâncias poderiam enviar o mesmo aviso duas vezes.
- Sem limite de requisições por IP no nginx (só o bloqueio de login por e-mail do backend).
- Sem monitoramento nem métricas além de `/actuator/health`; sem OpenTelemetry.
- Recursos dos containers sem limite definido (defina `mem_limit` conforme o servidor).

## Checklist de segurança antes de abrir para usuários
- [ ] `DB_PASSWORD` e `JWT_SECRET` fortes e únicos, só no `.env` do servidor
- [ ] `ADMIN_EMAIL`/`ADMIN_PASSWORD` removidos do `.env` depois do primeiro acesso
- [ ] HTTPS na frente do nginx, com HSTS
- [ ] Porta do banco não exposta (o compose já publica só em 127.0.0.1)
- [ ] Backup testado (restauração feita ao menos uma vez)
- [ ] Senha do administrador trocada em "Minha senha" por uma que só você conhece
