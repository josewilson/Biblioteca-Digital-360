import { useEffect, useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ApiError, api } from '../lib/api'
import type { Book, IsbnSuggestion, NameItem } from '../lib/types'
import { parseIsbns, MAX_BATCH } from '../lib/isbnBatch'
import { Alert, Badge, Button, Card, ErrorAlert, Field, PageHeader, Table, cellClass, inputClass } from '../components/ui'

/** Intervalo entre consultas (as APIs públicas têm limite de uso). Nos testes não espera. */
const GAP_MS = import.meta.env.MODE === 'test' ? 0 : 400

type State = 'consultando' | 'encontrada' | 'sem-titulo' | 'ja-cadastrada' | 'repetida' | 'nao-encontrada' | 'invalida' | 'erro' | 'cadastrada' | 'falhou'

interface Row {
  input: string
  state: State
  suggestion?: IsbnSuggestion
  message?: string
  selected: boolean
  bookId?: number
}

const LABELS: Record<State, { text: string; tone: 'neutral' | 'green' | 'amber' | 'red' | 'blue' }> = {
  consultando: { text: 'Consultando…', tone: 'blue' },
  encontrada: { text: 'Encontrada', tone: 'green' },
  'sem-titulo': { text: 'Sem título', tone: 'amber' },
  'ja-cadastrada': { text: 'Já cadastrada', tone: 'amber' },
  repetida: { text: 'Repetida na lista', tone: 'neutral' },
  'nao-encontrada': { text: 'Não encontrada', tone: 'red' },
  invalida: { text: 'ISBN inválido', tone: 'red' },
  erro: { text: 'Erro temporário', tone: 'red' },
  cadastrada: { text: 'Cadastrada', tone: 'green' },
  falhou: { text: 'Não cadastrada', tone: 'red' },
}

const pause = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))
const normalized = (name: string) => name.trim().toLowerCase()

/**
 * Importação em lote: consulta cada ISBN (um de cada vez, com intervalo), mostra a tabela de revisão e só grava as obras
 * que o usuário marcar. Cada obra nasce com 1 exemplar de consulta e 1 emprestável, com códigos gerados a partir do ISBN
 * (podem ser editados depois na obra).
 */
export function ImportBooksPage() {
  const queryClient = useQueryClient()
  const [text, setText] = useState('')
  const [rows, setRows] = useState<Row[]>([])
  const [ignored, setIgnored] = useState(0)
  const [truncated, setTruncated] = useState(false)
  const [createNames, setCreateNames] = useState(false)
  const [progress, setProgress] = useState('')
  const stop = useRef(false)

  useEffect(() => () => { stop.current = true }, [])   // saiu da página: para de consultar

  const patch = (index: number, change: Partial<Row>) => setRows((all) => all.map((r, i) => (i === index ? { ...r, ...change } : r)))

  async function lookupOne(index: number, input: string) {
    patch(index, { state: 'consultando', message: undefined })
    try {
      const found = await api<IsbnSuggestion>(`/books/isbn/${encodeURIComponent(input)}`)
      if (found.alreadyRegistered) {
        return patch(index, { state: 'ja-cadastrada', suggestion: found, selected: false })
      }
      setRows((all) => {
        const repeated = all.some((r, i) => i !== index && r.suggestion?.isbn === found.isbn && !r.suggestion.alreadyRegistered)
        const state: State = repeated ? 'repetida' : found.title ? 'encontrada' : 'sem-titulo'
        return all.map((r, i) => (i === index ? { ...r, state, suggestion: found, selected: state === 'encontrada' } : r))
      })
    } catch (e) {
      const status = e instanceof ApiError ? e.status : 0
      const state: State = status === 404 ? 'nao-encontrada' : status === 400 ? 'invalida' : 'erro'
      patch(index, { state, message: e instanceof Error ? e.message : 'Erro inesperado.', selected: false })
    }
  }

  /** Consulta em sequência, com intervalo. Se o usuário parar, o que sobrou fica como "erro" e pode ser tentado de novo. */
  async function runLookups(list: { index: number; input: string }[]) {
    stop.current = false
    for (const [n, { index, input }] of list.entries()) {
      if (stop.current) {
        list.slice(n).forEach((r) => patch(r.index, { state: 'erro', message: 'Consulta interrompida.', selected: false }))
        break
      }
      setProgress(`Consultando ${n + 1} de ${list.length}…`)
      await lookupOne(index, input)
      if (n < list.length - 1) await pause(GAP_MS)
    }
    setProgress('')
  }

  function start() {
    const parsed = parseIsbns(text)
    setIgnored(parsed.ignored)
    setTruncated(parsed.truncated)
    setRows(parsed.isbns.map((input) => ({ input, state: 'consultando', selected: false })))
    void runLookups(parsed.isbns.map((input, index) => ({ index, input })))
  }

  const save = useMutation({
    mutationFn: async () => {
      const chosen = rows.map((r, i) => ({ r, i })).filter(({ r }) => r.selected && r.state === 'encontrada')
      // Editoras e autores novos só são criados se o usuário pediu; categorias novas nunca (os assuntos das bases são ruidosos).
      const ids = { publishers: new Map<string, number>(), authors: new Map<string, number>() }
      if (createNames) {
        const [publishers, authors] = await Promise.all([api<NameItem[]>('/publishers'), api<NameItem[]>('/authors')])
        publishers.forEach((p) => ids.publishers.set(normalized(p.name), p.id))
        authors.forEach((a) => ids.authors.set(normalized(a.name), a.id))
      }
      const idFor = async (kind: 'publishers' | 'authors', name: string) => {
        const known = ids[kind].get(normalized(name))
        if (known != null) return known
        const created = await api<NameItem>(`/${kind}`, { method: 'POST', body: { name } })
        ids[kind].set(normalized(name), created.id)
        return created.id
      }
      for (const { r, i } of chosen) {
        const s = r.suggestion!
        try {
          const publisherId = s.publisher?.id ?? (createNames && s.publisher ? await idFor('publishers', s.publisher.name) : null)
          const authorIds: number[] = []
          for (const a of s.authors) {
            const id = a.id ?? (createNames ? await idFor('authors', a.name) : null)
            if (id != null) authorIds.push(id)
          }
          const saved = await api<Book>('/books', {
            method: 'POST',
            body: {
              title: s.title,
              isbn: s.isbn,
              edition: s.edition,
              publicationYear: s.publicationYear,
              language: s.language,
              description: s.description,
              coverUrl: s.coverUrl,
              publisherId,
              authorIds,
              categoryIds: s.categories.filter((c) => c.id != null).map((c) => c.id),
              copies: [
                { barcode: `${s.isbn}-C`, kind: 'CONSULTA', condition: 'BOM' },
                { barcode: `${s.isbn}-E`, kind: 'CIRCULANTE', condition: 'BOM' },
              ],
            },
          })
          patch(i, { state: 'cadastrada', bookId: saved.id, selected: false, message: undefined })
        } catch (e) {
          patch(i, { state: 'falhou', message: e instanceof Error ? e.message : 'Erro inesperado.' })
        }
      }
      void queryClient.invalidateQueries({ queryKey: ['books'] })
      void queryClient.invalidateQueries({ queryKey: ['publishers'] })
      void queryClient.invalidateQueries({ queryKey: ['authors'] })
    },
  })

  const busy = progress !== '' || save.isPending
  const selected = rows.filter((r) => r.selected && r.state === 'encontrada').length
  const failed = rows.map((r, i) => ({ r, i })).filter(({ r }) => r.state === 'erro')
  const done = rows.filter((r) => r.state === 'cadastrada').length
  const count = (state: State) => rows.filter((r) => r.state === state).length

  return (
    <>
      <PageHeader
        title="Importar obras por ISBN"
        subtitle="Cole uma lista de ISBNs, revise o que foi encontrado e cadastre só o que quiser. Nada é gravado antes da sua confirmação."
        actions={
          <Link to="/acervo" className="text-sm font-medium text-brand-700 underline">
            Voltar ao acervo
          </Link>
        }
      />

      <Card>
        <div className="space-y-3 p-4">
          <Field label="ISBNs" hint={`Um por linha, ou separados por vírgula ou ponto e vírgula (aceita uma coluna de CSV). Até ${MAX_BATCH} por vez.`}>
            {(p) => <textarea {...p} rows={6} className={inputClass} value={text} onChange={(e) => setText(e.target.value)} />}
          </Field>
          <div className="flex flex-wrap items-center gap-2">
            <Button disabled={!text.trim() || busy} loading={progress !== ''} onClick={start}>
              Consultar
            </Button>
            {progress !== '' && (
              <Button variant="secondary" onClick={() => (stop.current = true)}>
                Parar
              </Button>
            )}
            {progress !== '' && <span role="status" className="text-sm text-slate-700">{progress}</span>}
          </div>
          {ignored > 0 && <Alert tone="amber">{ignored} {ignored === 1 ? 'item foi ignorado' : 'itens foram ignorados'} por não parecer ISBN (10 ou 13 dígitos).</Alert>}
          {truncated && <Alert tone="amber">Só os {MAX_BATCH} primeiros ISBNs foram usados. Importe o resto em outro lote.</Alert>}
        </div>
      </Card>

      {rows.length > 0 && (
        <div className="mt-4 space-y-3">
          <Card>
            <Table caption="Revisão da importação" head={['Incluir', 'ISBN', 'Título', 'Autores', 'Editora', 'Situação']}>
              {rows.map((r, i) => {
                const s = r.suggestion
                const label = LABELS[r.state]
                return (
                  <tr key={`${r.input}-${i}`}>
                    <td className={cellClass}>
                      {r.state === 'encontrada' && (
                        <input type="checkbox" aria-label={`Incluir ${r.input}`} checked={r.selected} disabled={busy} onChange={(e) => patch(i, { selected: e.target.checked })} />
                      )}
                    </td>
                    <td className={cellClass}>{r.input}</td>
                    <td className={cellClass}>{s?.title ?? '—'}</td>
                    <td className={cellClass}>{s && s.authors.length > 0 ? s.authors.map((a) => a.name).join(', ') : '—'}</td>
                    <td className={cellClass}>{s?.publisher?.name ?? '—'}</td>
                    <td className={cellClass}>
                      <Badge tone={label.tone}>{label.text}</Badge>
                      {r.state === 'ja-cadastrada' && s?.existingBookId != null && (
                        <>
                          {' '}
                          <Link to={`/acervo/${s.existingBookId}`} className="text-sm text-brand-700 underline">
                            Abrir obra
                          </Link>
                        </>
                      )}
                      {r.state === 'cadastrada' && r.bookId != null && (
                        <>
                          {' '}
                          <Link to={`/acervo/${r.bookId}`} className="text-sm text-brand-700 underline">
                            Abrir obra
                          </Link>
                        </>
                      )}
                      {(r.state === 'erro' || r.state === 'falhou' || r.state === 'nao-encontrada' || r.state === 'invalida') && r.message && (
                        <p className="mt-1 text-sm text-slate-700">{r.message}</p>
                      )}
                    </td>
                  </tr>
                )
              })}
            </Table>
          </Card>

          <p className="text-sm text-slate-700">
            {count('encontrada') + count('cadastrada') + count('falhou')} encontradas · {count('ja-cadastrada')} já cadastradas · {count('nao-encontrada')} não encontradas
            {count('invalida') > 0 && ` · ${count('invalida')} inválidas`}
            {count('erro') > 0 && ` · ${count('erro')} com erro temporário`}
          </p>

          {failed.length > 0 && !busy && (
            <Button variant="secondary" onClick={() => void runLookups(failed.map(({ r, i }) => ({ index: i, input: r.input })))}>
              Tentar de novo os {failed.length} com erro
            </Button>
          )}

          <label className="flex items-start gap-2 text-sm">
            <input type="checkbox" className="mt-1" checked={createNames} onChange={(e) => setCreateNames(e.target.checked)} />
            <span>
              Cadastrar também as editoras e os autores novos. Sem isto, só entram editora e autores que já existem no cadastro.
              Categorias novas nunca são criadas aqui (os assuntos das bases costumam vir em inglês e ruidosos).
            </span>
          </label>

          <ErrorAlert error={save.error} />
          {done > 0 && !save.isPending && <Alert tone="green">{done} {done === 1 ? 'obra cadastrada' : 'obras cadastradas'}. Os exemplares têm códigos gerados a partir do ISBN (…-C consulta, …-E emprestável); edite-os na obra se precisar.</Alert>}
          <Button disabled={selected === 0 || busy} loading={save.isPending} onClick={() => save.mutate()}>
            {`Cadastrar ${selected} ${selected === 1 ? 'obra selecionada' : 'obras selecionadas'}`}
          </Button>
        </div>
      )}
    </>
  )
}
