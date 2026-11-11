import { useState, type FormEvent } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { Search } from 'lucide-react'
import { api } from '../lib/api'
import type { CopyCondition, CopyKind, CopyListItem, CopyStatus, Page } from '../lib/types'
import {
  Button,
  Card,
  Empty,
  ErrorAlert,
  Field,
  Loading,
  PageHeader,
  Pagination,
  StatusBadge,
  Table,
  cellClass,
  inputClass,
} from '../components/ui'

const STATUSES: [CopyStatus, string][] = [
  ['DISPONIVEL', 'Disponível'],
  ['EMPRESTADO', 'Emprestado'],
  ['RESERVADO', 'Reservado'],
  ['PERDIDO', 'Perdido'],
  ['BAIXADO', 'Baixado'],
]
const KINDS: [CopyKind, string][] = [
  ['CIRCULANTE', 'Emprestável'],
  ['CONSULTA', 'Consulta'],
]
const CONDITIONS: [CopyCondition, string][] = [
  ['NOVO', 'Novo'],
  ['BOM', 'Bom'],
  ['REGULAR', 'Regular'],
  ['RUIM', 'Ruim'],
]
const CONDITION_LABEL = Object.fromEntries(CONDITIONS) as Record<CopyCondition, string>

interface Filters {
  q: string
  status: string
  kind: string
  condition: string
  categoryId: string
}
const NONE: Filters = { q: '', status: '', kind: '', condition: '', categoryId: '' }

/** Listagem de exemplares (um por linha) com busca por texto e filtros; só a equipe vê. */
export function CopiesPage() {
  const [text, setText] = useState('')
  const [filters, setFilters] = useState<Filters>(NONE)
  const [page, setPage] = useState(0)

  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api<{ id: number; name: string }[]>('/categories') })
  const { data, isLoading, error } = useQuery({
    queryKey: ['copies', filters, page],
    queryFn: () => api<Page<CopyListItem>>('/copies', { params: { ...filters, page, size: 20 } }),
    placeholderData: keepPreviousData,
  })

  function change(patch: Partial<Filters>) {
    setPage(0)
    setFilters((f) => ({ ...f, ...patch }))
  }

  function search(e: FormEvent) {
    e.preventDefault()
    change({ q: text.trim() })
  }

  function clear() {
    setText('')
    setPage(0)
    setFilters(NONE)
  }

  const filtered = Object.values(filters).some((v) => v !== '')

  return (
    <>
      <PageHeader
        title="Exemplares"
        subtitle="Todos os exemplares do acervo, um por linha. Busque por título, autor, editora, categoria, ISBN, código ou localização."
      />

      <form onSubmit={search} role="search" className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="sm:col-span-2 lg:col-span-4 flex items-end gap-2">
          <div className="flex-1">
            <Field label="Buscar exemplares">
              {(p) => <input {...p} className={inputClass} value={text} onChange={(e) => setText(e.target.value)} />}
            </Field>
          </div>
          <Button type="submit" variant="secondary">
            <Search className="h-4 w-4" aria-hidden="true" />
            Buscar
          </Button>
        </div>
        <Field label="Situação">
          {(p) => (
            <select {...p} className={inputClass} value={filters.status} onChange={(e) => change({ status: e.target.value })}>
              <option value="">Todas</option>
              {STATUSES.map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field label="Tipo">
          {(p) => (
            <select {...p} className={inputClass} value={filters.kind} onChange={(e) => change({ kind: e.target.value })}>
              <option value="">Todos</option>
              {KINDS.map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field label="Estado de conservação">
          {(p) => (
            <select {...p} className={inputClass} value={filters.condition} onChange={(e) => change({ condition: e.target.value })}>
              <option value="">Todos</option>
              {CONDITIONS.map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field label="Categoria (disciplina)">
          {(p) => (
            <select {...p} className={inputClass} value={filters.categoryId} onChange={(e) => change({ categoryId: e.target.value })}>
              <option value="">Todas</option>
              {(categories.data ?? []).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          )}
        </Field>
      </form>

      {filtered && (
        <p className="mb-3 text-sm">
          <button type="button" onClick={clear} className="font-medium text-brand-700 underline">
            Limpar filtros
          </button>
        </p>
      )}

      <ErrorAlert error={error} />
      {isLoading && <Loading />}
      {data && (
        <Card>
          {data.content.length === 0 ? (
            <Empty>Nenhum exemplar encontrado.</Empty>
          ) : (
            <>
              <p className="px-4 pt-3 text-sm text-slate-600" aria-live="polite">
                {data.totalElements === 1 ? '1 exemplar' : `${data.totalElements} exemplares`}
              </p>
              <Table caption="Exemplares do acervo" head={['Código', 'Obra', 'Autores', 'Categorias', 'Tipo', 'Situação', 'Estado', 'Localização']}>
                {data.content.map((c) => (
                  <tr key={c.id}>
                    <td className={`${cellClass} font-mono text-xs`}>{c.barcode}</td>
                    <td className={cellClass}>
                      <Link to={`/acervo/${c.bookId}`} className="font-serif font-medium text-brand-700 underline">
                        {c.title}
                      </Link>
                      {c.publisher && <div className="text-xs text-slate-500">{c.publisher}</div>}
                    </td>
                    <td className={cellClass}>{c.authors.join(', ') || '—'}</td>
                    <td className={cellClass}>{c.categories.join(', ') || '—'}</td>
                    <td className={cellClass}>
                      <StatusBadge value={c.kind} />
                    </td>
                    <td className={cellClass}>
                      <StatusBadge value={c.status} />
                    </td>
                    <td className={cellClass}>{CONDITION_LABEL[c.condition] ?? c.condition}</td>
                    <td className={cellClass}>{c.location ?? '—'}</td>
                  </tr>
                ))}
              </Table>
              <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />
            </>
          )}
        </Card>
      )}
    </>
  )
}
