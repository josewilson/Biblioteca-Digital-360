# Regras de negócio

Status: **definidas**; só as marcadas (banco) já são impostas pelo código atual.

| Regra | Onde é imposta |
|---|---|
| Obra ≠ exemplar; cada exemplar tem situação própria | esquema (`books`, `book_copies`) |
| Toda obra tem ≥ 1 exemplar `CONSULTA` e ≥ 1 `CIRCULANTE` | **aplicação** (`BookService.create`, testado) |
| Não se dá baixa/perda no único exemplar de consulta ativo (cadastre outro antes) | **aplicação** (`BookService.changeStatus`, testado) |
| Exemplar `CONSULTA` nunca é emprestado | **banco** (FK composta + CHECK) |
| Todos os leitores usam o exemplar de consulta no local; visitante não empresta | aplicação (Fase 5) — pendente |
| Exemplar não tem dois empréstimos ativos | **banco** (índice único parcial) |
| Limite de empréstimos por usuário, prazos e renovação | configuráveis; aplicação (Fase 5) — pendente |
| Multa por dia de atraso, configurável, em reais | `fine_rates` (valores iniciais: aluno 1,00, professor 0,50, visitante 2,00); cálculo na Fase 5 |
| Uma multa por empréstimo; um pagamento por multa | **banco** (UNIQUE) |
| Isenção exige justificativa e responsável | **banco** (CHECK) |
| Fila de reservas por ordem cronológica | esquema (`queued_at`); lógica na Fase 5 |

## Regras do acervo e do empréstimo (definidas pelo usuário)
- Cada obra tem **vários exemplares emprestáveis** e **sempre pelo menos 1 de consulta**, que **jamais** é emprestado.
- Aluno, professor e funcionário podem ter **até 3 exemplares emprestados ao mesmo tempo** (`loan_limits`, configurável, padrão 3). Visitante não empresta.
- Contagem e bloqueio do limite: **implementados** (`LoanService.lend`, com trava do leitor; testado com requisições simultâneas).

## Empréstimo, devolução, multa e renovação (Fase 5, parte 1)
- **Empréstimo** recusado se: leitor inativo; perfil sem política em `loan_limits` (ex.: visitante); limite do perfil atingido; leitor com empréstimo atrasado; leitor com multa `ABERTA`; exemplar de consulta; exemplar não `DISPONIVEL`.
- **Perfil do leitor com vários perfis:** vale a política com maior limite e a maior multa por dia.
- **Prazo, renovações e dias por renovação** vêm de `loan_limits` (padrões **assumidos**: 14 dias, 2 renovações de 7 dias — a administração deve ajustar).
- **Multa:** dias corridos de atraso (pela data na zona `America/Sao_Paulo`; horas de atraso no mesmo dia do vencimento não cobram) × valor do perfil em `fine_rates`. Perfil sem valor configurado **não paga multa**. Funcionário tem valor padrão **assumido** de R$ 1,00/dia (V5): ajuste conforme a instituição. Uma multa por empréstimo.
- **Aviso da multa** é gravado em `notifications` na mesma transação; o envio por e-mail é da Fase 6 (nunca dentro da transação).
- **Renovação:** só empréstimo ativo e não atrasado, até o limite de renovações; novo prazo = prazo atual + dias da renovação.
- **Concorrência:** travas de linha no leitor, no exemplar e no empréstimo + índice único parcial no banco.


## Reservas, pagamento e isenção (Fase 5, parte 2)
- **Quem reserva:** só quem pode emprestar (visitante não). Uma reserva aberta por leitor e obra. Só se reserva quando **não há** exemplar emprestável disponível (havendo, empresta-se direto).
- **Fila:** ordem de cadastro. Quando um exemplar emprestável fica livre (devolução, exemplar novo, exemplar restaurado, reserva cancelada ou expirada), ele passa a `RESERVADO` e é guardado para o 1º da fila, com prazo de retirada (`reservation_pickup_days`, padrão **assumido** de 3 dias).
- **Retirada:** só o dono da reserva leva o exemplar guardado, dentro do prazo (empréstimo normal em `POST /loans`). Prazo vencido: retirada recusada na hora; a varredura (a cada 5 min) marca `EXPIRADA` e passa o exemplar ao próximo.
- **Renovação** é recusada se houver gente na fila da obra.
- Leitor que perdeu o direito de emprestar enquanto esperava sai da fila sem travar os demais.
- **Pagamento:** só o valor integral da multa, uma única vez (trava + UNIQUE); forma: `DINHEIRO`, `PIX`, `CARTAO_DEBITO`, `CARTAO_CREDITO`. Sem integração com provedor financeiro. O comprovante é `GET /payments/{id}`.
- **Isenção:** só `SUPER_ADMIN`, `ADMIN_INSTITUTION` e `LIBRARIAN`, com justificativa obrigatória; registra o responsável. Multa paga não se isenta e isenta não se paga.
- Multa em aberto continua bloqueando novos empréstimos; paga ou isenta libera.
