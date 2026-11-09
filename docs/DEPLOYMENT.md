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
Sem HTTPS, senha e token trafegam em texto puro. O arquivo `docker-compose.tls.yml` põe um **Caddy** na frente do site: ele pede e renova o certificado (Let's Encrypt) sozinho e envia HSTS.
1. O domínio precisa apontar para o servidor e as portas 80 e 443 estar abertas.
2. No `.env`: `DOMAIN=biblioteca.exemplo.com.br` e `WEB_BIND=127.0.0.1` (fecha o acesso direto ao site, sem HTTPS).
3. `docker compose -f docker-compose.yml -f docker-compose.tls.yml --profile full up -d --build`
O nginx passa a confiar no `X-Forwarded-For` do Caddy (só da rede interna do compose), então a auditoria e o limite de login enxergam o IP real do cliente. Não apague o volume `caddy_data`: o Let's Encrypt limita quantos certificados emite por domínio.
Testado com `DOMAIN=localhost` (certificado interno do Caddy): HTTPS, HSTS e redirecionamento de http. **Não testado com domínio público e Let's Encrypt.** Se preferir outro proxy (Traefik, balanceador da nuvem), ele precisa enviar HSTS e `X-Forwarded-For`, e o `deploy/real-ip-proxy.conf` deve listar a rede dele.

## E-mail
Por padrão os avisos só vão para o log. Para enviar de verdade: `APP_NOTIFICATIONS_CHANNEL=smtp`, `APP_NOTIFICATIONS_FROM` e `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD` no `.env`. **Não testado contra servidor real.**

## Dados e backup
O banco fica no volume `pgdata`. O serviço `backup` (perfil `full`) grava um dump por dia em `/backups` (volume `backups`) e apaga os com mais de `BACKUP_KEEP_DAYS` dias (padrão 14). O primeiro dump sai logo ao subir e pode estar vazio se o backend ainda não tiver migrado o banco; o seguinte já vem completo.
- **Copie os dumps para fora do servidor.** O volume está na mesma máquina: se o disco morrer, o backup morre junto. Ex.: `docker compose --profile full cp backup:/backups ./backups-copia`, agendado e enviado a outro lugar.
- **Teste a restauração:** `scripts/restore-test.sh` carrega o dump mais recente num banco temporário, confere tabelas, migrações e usuários, e apaga o temporário. Rode depois de subir e de vez em quando.
- **Restaurar de verdade** (num banco vazio): `docker compose --profile full exec -T backup cat /backups/ARQUIVO.dump | docker compose exec -T postgres pg_restore -U biblioteca -d biblioteca --no-owner`
- Backup manual antes de uma atualização: `docker compose --profile full exec -T backup pg_dump -Fc -f /backups/antes-da-atualizacao.dump` (esse nome não é apagado pela rotina).

## Atualizar
`git pull` e `docker compose --profile full up -d --build`. As migrações do banco (Flyway) rodam sozinhas ao iniciar o backend. A V9 cria as extensões `pg_trgm` e `unaccent`: o usuário do banco precisa ser dono dele (é o caso no compose). Depois dela, todos precisam entrar de novo. Migrações já aplicadas nunca devem ser editadas.

## Verificar
- `docker compose --profile full ps` (colunas de saúde: healthy).
- `curl http://localhost:3000/` (site) e, de dentro da rede dos containers, `/actuator/health` do backend. O `/actuator/health` **não** é exposto pelo nginx.
- Logs: `docker compose --profile full logs -f backend`.

## Restrições conhecidas
- **Uma instância só do backend.** A entrega de avisos e a expiração de reservas rodam em tarefas agendadas sem trava entre instâncias; duas instâncias poderiam enviar o mesmo aviso duas vezes.
- Limite de requisições por IP só no login (30 por minuto, resposta 429); soma-se ao bloqueio por e-mail do backend.
- **Monitoramento:** `/healthz` (no site, porta do nginx) responde `{"status":"UP"}` e serve para um monitor externo (UptimeRobot, Uptime Kuma). Não há métricas nem alertas próprios; sem OpenTelemetry. Os containers têm `healthcheck` e reiniciam sozinhos.
- Limites de memória (`mem_limit`): banco 512 MB, backend 768 MB, site 128 MB, backup 128 MB. São palpites para um servidor pequeno; ajuste ao seu.
- Logs do Docker com rotação (5 arquivos de 10 MB por container). Não há coleta central.

## Checklist de segurança antes de abrir para usuários
- [ ] `DB_PASSWORD` e `JWT_SECRET` fortes e únicos, só no `.env` do servidor
- [ ] `ADMIN_EMAIL`/`ADMIN_PASSWORD` removidos do `.env` depois do primeiro acesso
- [ ] HTTPS ligado (`docker-compose.tls.yml`) e `WEB_BIND=127.0.0.1`
- [ ] Cópia dos backups para fora do servidor e `scripts/restore-test.sh` aprovado
- [ ] Monitor externo apontado para `https://SEU-DOMINIO/healthz`
- [ ] Porta do banco não exposta (o compose já publica só em 127.0.0.1)
- [ ] Senha do administrador trocada em "Minha senha" por uma que só você conhece
