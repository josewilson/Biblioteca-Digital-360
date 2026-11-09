# Roadmap

- [x] Fase 1 — Diagnóstico, AGENTS.md, LEIA-ME.md
- [x] Fase 2 — Fundação: Spring Boot, Flyway V1, erros, testes. (Pendente: Dockerfile da aplicação, CI, OpenAPI)
- [x] Fase 3 — Segurança e usuários (JWT, perfis, cadastro). Pendentes em docs/SECURITY.md
- [x] Fase 4 — Acervo (obras, exemplares, autores, editoras, categorias). Pendentes: upload de capa, edição/exclusão de obra
- [x] Fase 5 — Empréstimo, devolução, renovação, reservas, multa, pagamento e isenção
- [x] Fase 6 — Painel, relatórios, notificações (backend) e interface web React. Pendentes: OpenAPI/Swagger, edição/exclusão de obra, upload de capa
- [x] Fase 7 — Segurança, desempenho, Docker, E2E (Playwright + axe), CI/Dependabot. **Pendentes:** HTTPS/HSTS (proxy com certificado), teste com leitor de tela, teste de carga
- [x] Pré-produção, passo 1 — Troca e redefinição de senha, senha temporária, revogação imediata de sessão, edição de obra/exemplar/usuário/cadastros, reativação de usuário, regras de empréstimo pela tela, busca sem acento (índices trigram, V9), seletores com busca no servidor
- [x] Pré-produção, passo 2 — Dependências: nginx 1.29; Dependabot não propõe mais Spring Boot de versão maior nem a imagem do Maven (o projeto fica no Java 21). **A migração para o Spring Boot 4 continua pendente** (o PR do Dependabot falhava no CI)
- [x] Pré-produção, passo 3 — Ambiente: HTTPS opcional (Caddy + HSTS), IP real atrás do proxy, limite de login por IP, limites de memória e rotação de logs, backup diário com teste de restauração, `/healthz`. **Não validado:** certificado Let's Encrypt com domínio público
- [x] Pré-produção, passo 4 — LGPD: exportar os próprios dados e os de um usuário, anonimizar com bloqueio por pendências, retenção de 90 dias das tentativas de login, página `/privacidade` (ver `docs/LGPD.md`). **Pendente:** texto jurídico revisado, encarregado (DPO), retenção de contas inativas
- [ ] Pré-produção, próximos passos — SMTP real (testar com o provedor escolhido), cópia dos backups para fora do servidor, alertas
