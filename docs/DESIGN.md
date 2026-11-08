# Design da interface

Aplicado com a skill `frontend-design` (plugin oficial da Anthropic). O briefing do projeto manda: *"padrão visual de sistema institucional moderno, identidade consistente, sem excesso de elementos decorativos, transmitir confiabilidade, organização e profissionalismo"*. Quando o briefing define a direção, ele vence; o design abaixo é sóbrio de propósito e concentra sua personalidade em **um** elemento.

## Assunto
Gestão de biblioteca escolar. **Quem usa:** (1) bibliotecários e auxiliares no balcão, em sessões longas no computador, onde velocidade e ausência de erro importam; (2) alunos e professores olhando seus empréstimos no celular. **Tarefa principal:** registrar empréstimo e devolução sem erro e enxergar, de relance, o que está atrasado.

## Direção: a ficha de empréstimo
O vocabulário da biblioteca escolar é a ficha de papel e o **carimbo de data de devolução**. A interface é calma, em papel e verde-lombada, e a identidade mora num só lugar: o **carimbo de prazo** (`DueStamp`), que aparece onde há um empréstimo. O que é urgente vira carimbo vermelho; o resto fica quieto.

## Tokens
| Papel | Valor | Uso |
|---|---|---|
| Papel | `#F4F6F4` | fundo da página (cinza-esverdeado frio, não creme) |
| Tinta | `#1B2523` | texto principal |
| Lombada | `#24584A` | cor da marca: botões, item ativo, links (8,2:1 sobre branco) |
| Carimbo | `#B3261E` | só para atraso e multa em aberto (6,5:1 sobre branco) |
| Prazo | `#7A4B00` sobre `#FBEFD0` | vence hoje ou amanhã |
| Reserva | `#1D4E89` sobre `#E3EDF8` | exemplar reservado |

Neutros (`slate-*` do Tailwind) foram **redefinidos** em tons esverdeados do mesmo matiz, para a interface inteira ficar coerente sem pintar classe por classe. Texto secundário: `#5E6B67` (≥ 5:1 sobre papel e sobre branco).

## Tipografia
- **Atkinson Hyperlegible** (interface e corpo): criada pelo Braille Institute para máxima legibilidade, o que combina com o compromisso de acessibilidade do projeto. Algarismos tabulares nas tabelas.
- **Literata** (títulos de página e **títulos de obras**): desenhada para leitura longa; os nomes de livros em serifa deixam claro o que é "obra" no meio da interface.
- Duas famílias claramente distintas. Hospedadas por nós (`@fontsource`, licença OFL): a CSP só aceita fontes do próprio site e nenhum leitor tem o IP enviado a terceiros.
- Escala: 14 / 16 / 20 / 25 / 31 px (razão ~1,25), corpo 16 px, entrelinha 1,5. Linhas de texto longas limitadas a < 75 caracteres.

## Layout
```
┌─────────┬────────────────────────────────────────────────┐
│▌Biblioteca│ Balcão                                        │
│ Digital  │ ───────────────────────────────────────────── │
│ ─────    │ Novo empréstimo        (formulário no papel)    │
│ Painel   │                                                 │
│ Acervo   │ Empréstimos em andamento                        │
│▌Balcão   │  Obra (serifa)   Leitor     Devolver até        │
│ Reservas │  Dom Casmurro    Ana        ╔Atrasado há 3 dias╗│  ← carimbo
│ …        │  Iracema         Bruno      [Devolver até 20/11]│
└─────────┴────────────────────────────────────────────────┘
```
- **Menu lateral claro** (um degrau mais escuro que o papel) com uma faixa de lombada verde na borda; item ativo com barra verde e peso maior. No celular, vira o menu recolhível que já existe.
- **Menos caixas:** seções separadas por filetes e espaço; painel branco só onde há formulário ou gráfico.
- **Painel:** abre com **o que pede ação** (atrasados e multas em aberto, em carimbo vermelho, com atalho para a tela certa), e não com oito números iguais. Os totais do acervo ficam numa lista discreta.
- Alinhamento à esquerda em tudo; números de tabela alinhados à direita.

## Revisão contra o genérico (a skill pede)
| Tique que a skill lista | No primeiro rascunho | Decisão |
|---|---|---|
| Creme + serifa + terracota | Não usei: fundo é cinza-esverdeado frio e o acento quente é um vermelho de carimbo, só semântico | mantido |
| "Kit de cartões SaaS" (cartões idênticos, mesma sombra) | A versão anterior era exatamente isso (8 cartões de números iguais, sombra em tudo) | **corrigido:** sem sombra, painéis só onde agrupam de fato, painel de abertura reescrito |
| Rótulo em CAIXA ALTA acima de tudo | Cabeçalhos de tabela estavam em caixa alta | **corrigido:** frase normal, peso 600 |
| Linhas com pontos médios ("A · B · C") | O subtítulo da obra usava `·` | **corrigido:** vírgulas |
| `→` nos links | Não usado | mantido |
| Mono nos rótulos de dados | Não usado | mantido (algarismos tabulares da própria Atkinson) |
| Movimento por todo lado | Nenhum | mantido: só o que responde a ação (abrir diálogo) e respeitando `prefers-reduced-motion` |
| Numeração 01/02/03 sem sequência | Não usada | mantido |

## Onde está a ousadia (e só aí)
No **carimbo de prazo**. Todo o resto é deliberadamente discreto.

## Textos
Frases em linguagem de balcão, na voz do usuário, ação com o mesmo nome em todo o fluxo ("Registrar devolução" no botão, na confirmação e no aviso). Vazio vira convite: diz o que fazer em seguida.

## Piso de qualidade (conferido por ferramenta, não só por intenção)
Foco visível, contraste verificado pelo axe-core em todas as telas (desktop e celular), sem rolagem horizontal no celular, sem animação que o sistema operacional tenha pedido para reduzir. Ver `docs/TESTING.md`.

## Notas da revisão visual (com capturas reais)
- **Zero cortado:** a Atkinson Hyperlegible desenha o `0` com uma barra, de propósito, para distinguir de "O". É um ganho de legibilidade em datas, valores e códigos, então foi mantido nos dados; mas ficava estranho no nome da marca ("Digital 36Ø"), que por isso usa a Literata.
- **Animação de gráficos:** desligada (`isAnimationActive={false}`). Ninguém pediu o movimento e ele atrapalhava a captura e quem prefere menos movimento.
- **Estados vazios** compactos e com uma instrução do que fazer.
- **Achado de acessibilidade:** o quadro que rola a tabela na horizontal no celular não era alcançável por teclado; agora é (`role="region"`, nome e `tabindex`).
- **Achado de produto:** com muitos empréstimos ativos não havia como achar o de um leitor; o balcão ganhou "Filtrar por leitor".
