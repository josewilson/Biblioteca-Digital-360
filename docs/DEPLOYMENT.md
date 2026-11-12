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

## Render (plano gratuito, sem Docker Compose)
O `render.yaml` na raiz cria três serviços: banco Postgres, backend (Docker, `backend/Dockerfile`) e site estático (`frontend`, com rewrite de `/api` para o backend). HTTPS e domínio `onrender.com` vêm do Render; nginx, Caddy e o serviço de backup do compose **não são usados**.
1. No Render: **New → Blueprint**, escolha este repositório. Preencha os segredos pedidos: `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `APP_PUBLIC_URL` (o endereço do site, com o sufixo que o Render acrescenta: no primeiro deploy, `https://biblioteca-web-bcyz.onrender.com`), `PRIVACY_CONTACT` e, se for enviar e-mail, as variáveis de SMTP (veja "E-mail"; no Render gratuito use a porta 2525). `JWT_SECRET` é gerado pelo Render.
2. O Render acrescenta um sufixo ao nome do backend (no primeiro deploy: `https://biblioteca-api-86q3.onrender.com`). Se o seu endereço for outro, ajuste os dois `destination` do site em `render.yaml`. O backend leva uns 3 a 4 minutos para subir (de novo a cada despertar do plano gratuito).
3. Primeiro deploy: o backend leva alguns minutos. Entre no site, troque a senha do administrador e **apague `ADMIN_EMAIL` e `ADMIN_PASSWORD`** das variáveis.
4. Monitor externo (UptimeRobot) em `https://SEU-SITE/healthz` mantém o backend acordado.

Limites do plano gratuito e o que foi (ou não) testado. **Deploy real feito em 11/11/2026** (site `https://biblioteca-web-bcyz.onrender.com`, API `https://biblioteca-api-86q3.onrender.com`). **Conferido em 12/11/2026, sem login:** o rewrite de `/api` pelo site chega ao backend (`/healthz` devolve `UP`; login errado dá 401; `/api/v1/openapi` sem token dá 401), as rotas do React (`/acervo`, `/privacidade`) respondem 200 e os cabeçalhos de segurança (CSP, `X-Frame-Options`, `nosniff`) saem no site. O backend sobe e passa no health check, o que indica que a V9 rodou. Se o rewrite der 404, o `destination` em `render.yaml` não bate com o endereço real da API (foi o que aconteceu antes do commit `97b16f7`).
- O backend dorme sem acesso e o primeiro pedido depois disso demora (a JVM sobe do zero).
- O Postgres gratuito expira em cerca de 30 dias: serve para demonstração, não para dados reais. Sem o serviço `backup` do compose; para uso real, plano pago de banco (com backup do Render).
- A memória é de 512 MB: `JAVA_TOOL_OPTIONS` no `render.yaml` reduz o heap, e o valor é um palpite. Se o backend for morto por falta de memória, reduza `MaxRAMPercentage` ou use um plano pago.
- O limite de login por IP vivia no nginx e **não existe** no site estático; sobra o bloqueio por e-mail do backend. O IP gravado na auditoria é o do proxy do Render, não o do cliente.
- **Não conferido ainda:** login como administrador pelo site, busca sem acento no acervo (prova de que as extensões `pg_trgm`/`unaccent` e os índices da V9 funcionam no Postgres do Render), `APP_PUBLIC_URL` com o endereço real, envio de e-mail e o monitor externo.

## HTTPS (obrigatório fora da sua máquina)
Sem HTTPS, senha e token trafegam em texto puro. O arquivo `docker-compose.tls.yml` põe um **Caddy** na frente do site: ele pede e renova o certificado (Let's Encrypt) sozinho e envia HSTS.
1. O domínio precisa apontar para o servidor e as portas 80 e 443 estar abertas.
2. No `.env`: `DOMAIN=biblioteca.exemplo.com.br` e `WEB_BIND=127.0.0.1` (fecha o acesso direto ao site, sem HTTPS).
3. `docker compose -f docker-compose.yml -f docker-compose.tls.yml --profile full up -d --build`
O nginx passa a confiar no `X-Forwarded-For` do Caddy (só da rede interna do compose), então a auditoria e o limite de login enxergam o IP real do cliente. Não apague o volume `caddy_data`: o Let's Encrypt limita quantos certificados emite por domínio.
Testado com `DOMAIN=localhost` (certificado interno do Caddy): HTTPS, HSTS e redirecionamento de http. **Não testado com domínio público e Let's Encrypt.** Se preferir outro proxy (Traefik, balanceador da nuvem), ele precisa enviar HSTS e `X-Forwarded-For`, e o `deploy/real-ip-proxy.conf` deve listar a rede dele.

## E-mail
**Recuperação de senha:** o link do e-mail "Esqueci a senha" usa `APP_PUBLIC_URL` (padrão `http://localhost:3000`). Em produção, defina o endereço real, por exemplo `APP_PUBLIC_URL=https://biblioteca.sua-escola.com.br`. Sem SMTP configurado (canal `log`) o link só aparece no log do backend.
Por padrão os avisos só vão para o log. Para enviar de verdade, no `.env`:
```
APP_NOTIFICATIONS_CHANNEL=smtp
APP_NOTIFICATIONS_FROM=biblioteca@sua-escola.com.br   # remetente aprovado pelo provedor
SPRING_MAIL_HOST=smtp.seu-provedor.com
SPRING_MAIL_PORT=587          # 587 + STARTTLS (padrão); para a 465 use também MAIL_SMTP_SSL=true e MAIL_SMTP_STARTTLS=false
SPRING_MAIL_USERNAME=...
SPRING_MAIL_PASSWORD=...
```
**Provedor gratuito para começar (Brevo, 300 e-mails por dia):** crie a conta, valide o remetente em "Senders" e gere uma chave SMTP em "SMTP & API". No `.env`: `SPRING_MAIL_HOST=smtp-relay.brevo.com`, `SPRING_MAIL_PORT=2525` (STARTTLS, o padrão), `SPRING_MAIL_USERNAME` = o login SMTP que a Brevo mostra, `SPRING_MAIL_PASSWORD` = a chave SMTP, `APP_NOTIFICATIONS_FROM` = o remetente validado. **No Render gratuito use a porta 2525:** as portas 25, 465 e 587 são bloqueadas nos serviços gratuitos ([aviso do Render](https://render.com/changelog/free-web-services-will-no-longer-allow-outbound-traffic-to-smtp-ports)). Sem mudança de código; a Brevo aceitar a 2525 é do provedor e **precisa ser conferida com o teste abaixo**.
Servidor local sem senha (teste): `MAIL_SMTP_AUTH=false` e `MAIL_SMTP_STARTTLS=false`. O backend **recusa subir** com o canal `smtp` sem host, remetente ou (com autenticação ligada) usuário, para o erro aparecer na hora e não aviso a aviso. Conexão e leitura têm tempo limite (5 s e 10 s): provedor fora do ar não trava o sistema, e depois de 3 falhas seguidas a rodada de envio para e retoma no minuto seguinte.
**Confira com o provedor de verdade**, logado como administrador: `POST /api/v1/notifications/test` manda um e-mail de teste para o seu endereço e devolve o canal ligado (`log` não envia nada) ou o motivo do erro. O caminho de envio é testado contra um servidor SMTP de verdade (Mailpit em container, `SmtpChannelTest`: conexão, acentos, falha com servidor fora do ar). **Não testado com o seu provedor** (TLS, autenticação, limites de envio, remetente aprovado): antes de abrir, mande um aviso real a si mesmo. Se o provedor recusar, o aviso fica pendente e é tentado de novo (até 5 vezes, `notifications.last_error` mostra o motivo).

## Busca de dados pelo ISBN
O botão "Buscar por ISBN" do cadastro de obra faz o **servidor** consultar `brasilapi.com.br` e `openlibrary.org` (HTTPS, saída para a internet). Só o ISBN sai do servidor. Os endereços e o tempo limite ficam em `app.isbn.brasilapi-url`, `app.isbn.openlibrary-url` e `app.isbn.timeout` (padrão 5 s por provedor); sem internet o botão responde "Preencha manualmente" e o cadastro continua normal. A Open Library não exige chave. O Google Books não é usado: sem chave de API a cota anônima é zero.

## Busca de capa pelo ISBN
O botão "Buscar capa pelo ISBN" faz o **servidor** consultar `covers.openlibrary.org`: o backend precisa de saída para a internet (HTTPS). Só o ISBN sai do servidor, que não é dado pessoal. `COVER_LOOKUP_URL` muda o serviço (`%s` é o ISBN); **vazio desliga a busca** (o envio manual continua). Sem internet, o botão responde "Não foi possível consultar o serviço de capas". Cobertura: a Open Library não tem todos os livros, principalmente edições nacionais e antigas; nesses casos, envie a imagem.

## Privacidade
Preencha `PRIVACY_CONTACT` no `.env` com o encarregado (DPO) e o contato, por exemplo `Encarregado: Maria Silva, privacidade@sua-escola.com.br`. Aparece na página pública `/privacidade`. O texto da página é um rascunho do que o sistema faz: peça revisão jurídica antes de abrir (`docs/LGPD.md`).

## Dados e backup
O banco fica no volume `pgdata`. O serviço `backup` (perfil `full`) grava um dump por dia em `/backups` (volume `backups`) e apaga os com mais de `BACKUP_KEEP_DAYS` dias (padrão 14). O primeiro dump sai logo ao subir e pode estar vazio se o backend ainda não tiver migrado o banco; o seguinte já vem completo.
- **Cópia para fora do servidor (recomendado):** o volume está na mesma máquina, e se o disco morrer o backup morre junto. O serviço `backup-sync` (rclone) copia os dumps de hora em hora para um armazenamento externo e fica **desligado** enquanto `BACKUP_REMOTE` estiver vazio. Para S3 e compatíveis (AWS, Backblaze B2, Wasabi...), no `.env`:
  ```
  BACKUP_REMOTE=offsite:meu-bucket/biblioteca
  RCLONE_CONFIG_OFFSITE_TYPE=s3
  RCLONE_CONFIG_OFFSITE_PROVIDER=AWS            # ou Other, com RCLONE_CONFIG_OFFSITE_ENDPOINT=https://...
  RCLONE_CONFIG_OFFSITE_REGION=sa-east-1
  RCLONE_CONFIG_OFFSITE_ACCESS_KEY_ID=...
  RCLONE_CONFIG_OFFSITE_SECRET_ACCESS_KEY=...
  ```
  A cópia nunca apaga no destino: configure a retenção no próprio provedor (regra de ciclo de vida do bucket). Falhas aparecem no log (`docker compose --profile full logs backup-sync`). Testado com um endpoint S3 local; **não testado com o seu provedor**. Para Google Drive, SFTP etc., use as variáveis `RCLONE_CONFIG_OFFSITE_*` do tipo correspondente (documentação do rclone). Use uma chave de acesso que só grave nesse bucket.
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
- [ ] `BACKUP_REMOTE` preenchido (cópia externa) e `scripts/restore-test.sh` aprovado
- [ ] Busca de capa conferida em produção (botão com um ISBN conhecido) ou desligada com `COVER_LOOKUP_URL=` vazio
- [ ] `PRIVACY_CONTACT` preenchido e o texto de `/privacidade` revisado por quem responde juridicamente pela instituição
- [ ] `APP_PUBLIC_URL` com o endereço real e "Esqueci a senha" testado de ponta a ponta (pedir o link, receber, trocar a senha)
- [ ] E-mail conferido: botão "Enviar e-mail de teste" em Regras de empréstimo (administrador) recebeu a mensagem
- [ ] Monitor externo apontado para `https://SEU-DOMINIO/healthz`
- [ ] Porta do banco não exposta (o compose já publica só em 127.0.0.1)
- [ ] Senha do administrador trocada em "Minha senha" por uma que só você conhece
