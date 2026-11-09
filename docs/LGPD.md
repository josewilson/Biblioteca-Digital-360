# LGPD — dados pessoais no sistema

Este documento descreve o que o sistema **faz hoje**. Não é parecer jurídico: a instituição (controladora dos dados) precisa
revisar a política da tela `/privacidade`, definir o encarregado (DPO) e o canal de contato, e decidir os prazos de guarda.

## Quais dados pessoais existem
| Onde | Dado |
|---|---|
| `users` | nome, e-mail, matrícula, telefone, endereço, CPF (opcional), perfis, hash da senha |
| `loans`, `reservations`, `fines` (via empréstimo) | histórico de uso da biblioteca |
| `notifications` | texto dos avisos enviados (título de livro, prazo, valor de multa) |
| `audit_logs` | ação, IP e data; `LOGIN_FAILED`/`LOGIN_BLOCKED` guardam o e-mail digitado |

Coleta mínima: só nome, e-mail e perfil são obrigatórios. O CPF nunca é devolvido pela API de usuários nem aparece nas telas;
só sai na exportação dos dados, para o próprio titular e para administradores.

## Direitos do titular
| Direito | Como o sistema atende |
|---|---|
| Acesso | `GET /me/data` (a própria pessoa, qualquer perfil; botão "Baixar meus dados" em `/privacidade`) e `GET /users/{id}/export` (administradores, botão "Exportar dados" em Usuários). JSON com cadastro, perfis, empréstimos, reservas, multas, avisos e registro de acessos. |
| Correção | A equipe edita o cadastro em Usuários. |
| Eliminação | `POST /users/{id}/anonymize` (administradores; botão "Anonimizar"). Ver abaixo. |
| Informação | Página pública `/privacidade`, linkada no login e no menu. |

Toda exportação e toda anonimização entram na auditoria (`DATA_EXPORTED`, `USER_ANONYMIZED`).

## Anonimização
Irreversível. Recusada (422) enquanto a pessoa tiver **empréstimo em aberto, multa em aberto ou reserva em andamento**, e para
a própria conta de quem pede. Respeita a hierarquia (bibliotecário não mexe em administrador). Faz, numa transação:
- nome vira "Usuário anonimizado", e-mail vira `anonimizado-<id>@anonimo.invalid` (domínio que não existe: nenhum aviso sai);
- CPF, matrícula, telefone, endereço e senha são apagados; a conta é fechada e as sessões caem na hora;
- os avisos (`notifications`) da pessoa são apagados;
- IP e detalhes das linhas de auditoria da pessoa são apagados, inclusive os e-mails digitados em tentativas de login.

**O que fica, de propósito:** empréstimos, multas e pagamentos (necessários à contabilidade da biblioteca), agora sem nome;
e as linhas de auditoria com ação e data, sem IP. O texto livre do motivo de isenção de multa (`FINE_WAIVED`) não é tocado:
se a equipe digitou um nome ali, ele permanece.

## Retenção
- Tentativas de login falhas e bloqueios (`audit_logs`): apagados após 90 dias, todo dia às 03:30 (a regra de bloqueio só olha 15 minutos).
- Cadastro e histórico: enquanto a conta existir. **Não há prazo automático** para contas inativas: é decisão da instituição.
- Backups (`docs/DEPLOYMENT.md`) guardam 14 dias e **contêm os dados anteriores à anonimização**. Dado anonimizado volta a existir
  se um backup antigo for restaurado: reaplique as anonimizações depois de restaurar.

## Pendente
- Prazo de retenção para contas inativas e rotina que as anonimize.
- Texto jurídico revisado, encarregado (DPO) e canal de contato na política.
- Registro de consentimento, se a instituição tiver finalidade que o exija.
- Comunicação de incidente de segurança (processo, não código).
